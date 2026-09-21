package de.d88.platform.agent

import de.d88.platform.core.AuthorizationChain
import de.d88.platform.core.AuditTrail
import de.d88.platform.core.HumanGate
import de.d88.platform.debug.DebugGateway
import de.d88.platform.model.ActionRequest
import de.d88.platform.model.ActionCategory
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.AuthorizationResult
import de.d88.platform.model.Clock
import de.d88.platform.model.DataFlow
import de.d88.platform.model.Decision
import java.util.concurrent.ExecutorService

/**
 * Agent Runtime / Query-Workers (siehe docs/QUERY-WORKERS.md).
 *
 * Verarbeitung:
 *   Task → USER_REQUEST (Audit) → Plan (Planner) → PLAN_CREATED (Audit)
 *   → je Schritt: ActionRequest → AuthorizationChain →
 *       DENIED / APPROVED (ausführen) / HUMAN_GATE_REQUIRED (in Queue)
 *   → DEVICE_ACTION / DEVICE_RESULT / VALIDATION / CONTEXT_UPDATE (Audit)
 *
 * Ehrlichkeit: Schritte, die einen nicht verfügbaren Transport brauchen,
 * enden mit TRANSPORT_UNAVAILABLE – nie mit vorgetäuschtem Erfolg.
 */
class AgentRuntime(
    private val chain: AuthorizationChain,
    private val gate: HumanGate,
    private val audit: AuditTrail,
    private val gateway: DebugGateway,
    private val executor: ExecutorService,
    private val clock: Clock = Clock.SYSTEM
) {

    enum class StepState { PENDING, RUNNING, APPROVED, DENIED, WAITING_HUMAN, TRANSPORT_UNAVAILABLE, DONE }

    data class StepOutcome(
        val step: AgentStep,
        val state: StepState,
        val authorization: AuthorizationResult?,
        val detail: String
    )

    data class TaskOutcome(
        val task: AgentTask,
        val plan: AgentPlan,
        var steps: List<StepOutcome> = emptyList(),
        var finished: Boolean = false
    )

    interface Listener {
        fun onPlan(task: AgentTask, plan: AgentPlan)
        fun onStepUpdated(task: AgentTask, outcome: TaskOutcome)
        fun onFinished(task: AgentTask, outcome: TaskOutcome)
    }

    private val outcomes = LinkedHashMap<String, TaskOutcome>()

    @Synchronized
    fun outcome(taskId: String): TaskOutcome? = outcomes[taskId]

    @Synchronized
    fun allOutcomes(): List<TaskOutcome> = outcomes.values.toList()

    /** Reicht einen Task ein – Planung und Ausführung laufen asynchron. */
    fun submit(task: AgentTask, listener: Listener) {
        val userEvent = audit.record(
            AuditEventType.USER_REQUEST,
            actionId = task.taskId,
            deviceId = task.deviceId,
            sessionId = task.sessionId,
            projectId = task.projectId,
            result = task.intent
        )
        val taskEvent = audit.record(
            AuditEventType.TASK_CREATED,
            actionId = task.taskId,
            deviceId = task.deviceId,
            sessionId = task.sessionId,
            projectId = task.projectId,
            taskId = task.taskId,
            parentEventId = userEvent.eventId,
            result = "Task angelegt"
        )
        val plan = Planner.plan(task)
        audit.record(
            AuditEventType.PLAN_CREATED,
            actionId = task.taskId,
            deviceId = task.deviceId,
            sessionId = task.sessionId,
            projectId = task.projectId,
            parentEventId = taskEvent.eventId,
            result = "${plan.steps.size} Schritte"
        )
        val outcome = TaskOutcome(task, plan)
        outcomes[task.taskId] = outcome
        listener.onPlan(task, plan)

        executor.execute {
            try {
                val results = mutableListOf<StepOutcome>()
                for (step in plan.steps) {
                    val request = buildRequest(task, step)
                    val stepOutcome = runStep(task, step, request)
                    results += stepOutcome
                    outcome.steps = results.toList()
                    listener.onStepUpdated(task, outcome)
                }
                outcome.finished = true
                audit.record(
                    AuditEventType.CONTEXT_UPDATE,
                    actionId = task.taskId,
                    deviceId = task.deviceId,
                    sessionId = task.sessionId,
                    projectId = task.projectId,
                    result = "Task abgeschlossen: " +
                        results.joinToString(", ") { it.state.name }
                )
                listener.onFinished(task, outcome)
            } catch (e: Exception) {
                // Fail-Closed: unerwartete Worker-Fehler werden protokolliert,
                // der Task endet als fehlgeschlagen – nie als stiller Erfolg.
                outcome.finished = true
                audit.record(
                    AuditEventType.VALIDATION,
                    actionId = task.taskId,
                    deviceId = task.deviceId,
                    sessionId = task.sessionId,
                    result = "Worker-Fehler (Fail-Closed): ${e.message}"
                )
                listener.onFinished(task, outcome)
            }
        }
    }

    /**
     * Schritt nach Menschenfreigabe weiterführen (Human Gate → EXECUTION).
     */
    fun resumeAfterApproval(actionId: String, approver: String): Result<StepOutcome> = runCatching {
        val pending = gate.pending().first { it.action.actionId == actionId }
        val gatewayResult = gateway.executeAfterApproval(actionId, approver = approver)
        val outcome = when (gatewayResult) {
            is DebugGateway.Executed -> StepOutcome(
                pendingActionStep(pending), StepState.DONE, gatewayResult.authorization,
                if (gatewayResult.adbResult.realTransport) "Ausgeführt (echter Transport)"
                else "Lokal verarbeitet"
            )
            is DebugGateway.TransportUnavailable -> StepOutcome(
                pendingActionStep(pending), StepState.TRANSPORT_UNAVAILABLE,
                gatewayResult.authorization, gatewayResult.detail
            )
            is DebugGateway.Denied -> StepOutcome(
                pendingActionStep(pending), StepState.DENIED, gatewayResult.authorization,
                "Nach Freigabe abgelehnt"
            )
            is DebugGateway.WaitingForHuman -> StepOutcome(
                pendingActionStep(pending), StepState.WAITING_HUMAN, gatewayResult.authorization,
                "Weitere Freigabe erforderlich"
            )
        }
        updateTaskForAction(actionId, outcome)
        outcome
    }

    fun denyAction(actionId: String, approver: String, reason: String): Result<StepOutcome> = runCatching {
        val pending = gate.pending().first { it.action.actionId == actionId }
        val decision = gate.deny(actionId, approver, reason)
        val outcome = StepOutcome(
            pendingActionStep(pending),
            if (decision == Decision.DENIED) StepState.DENIED else StepState.WAITING_HUMAN,
            pending.result, "Abgelehnt: $reason"
        )
        updateTaskForAction(actionId, outcome)
        outcome
    }

    // ------------------------------------------------------------------ intern

    private fun buildRequest(task: AgentTask, step: AgentStep): ActionRequest = ActionRequest(
        actionId = "act-${task.taskId}-${step.stepId}",
        deviceId = task.deviceId,
        role = roleResolver(task.deviceId),
        projectId = task.projectId,
        taskId = task.taskId,
        sessionId = task.sessionId,
        capability = step.capability,
        target = task.deviceId,
        requestedChange = step.description,
        reason = task.intent,
        dataFlow = if (step.category == ActionCategory.DATA_EXPORT) DataFlow.EXTERNAL
        else if (step.adbCommand != null) DataFlow.DEVICE_TO_SERVER
        else DataFlow.LOCAL_ONLY,
        networkFlow = step.capability == de.d88.platform.model.Capability.NETWORK ||
            step.category == ActionCategory.NETWORK_ENABLE,
        riskClass = step.risk,
        category = step.category,
        evidence = task.details
    )

    private fun runStep(task: AgentTask, step: AgentStep, request: ActionRequest): StepOutcome {
        audit.record(
            AuditEventType.CAPABILITY_REQUESTED,
            actionId = request.actionId,
            deviceId = task.deviceId,
            sessionId = task.sessionId,
            projectId = task.projectId,
            capability = step.capability,
            parentEventId = null,
            result = step.description
        )
        val gatewayResult = if (step.adbCommand != null) {
            gateway.requestDeviceAction(request, step.adbCommand)
        } else {
            val authorization = chain.evaluate(request)
            when (authorization.decision) {
                Decision.APPROVED -> DebugGateway.Executed(
                    request, authorization,
                    de.d88.platform.debug.AdbResult(task.deviceId, "(local)", 0, "", "", clock.now(), false)
                )
                Decision.DENIED -> DebugGateway.Denied(request, authorization)
                Decision.HUMAN_GATE_REQUIRED -> DebugGateway.WaitingForHuman(
                    request, authorization,
                    gate.pending().first { it.action.actionId == request.actionId }
                )
            }
        }
        return when (gatewayResult) {
            is DebugGateway.Executed -> StepOutcome(step, StepState.DONE, gatewayResult.authorization,
                "Ausgeführt" + if (gatewayResult.adbResult.realTransport) " (echter Transport)" else " (lokal)")
            is DebugGateway.Denied -> StepOutcome(step, StepState.DENIED, gatewayResult.authorization,
                deniedReason(gatewayResult.authorization))
            is DebugGateway.WaitingForHuman -> StepOutcome(step, StepState.WAITING_HUMAN, gatewayResult.authorization,
                "Wartet auf Human Gate")
            is DebugGateway.TransportUnavailable -> StepOutcome(step, StepState.TRANSPORT_UNAVAILABLE,
                gatewayResult.authorization, gatewayResult.detail)
        }
    }

    private fun deniedReason(authorization: AuthorizationResult?): String =
        authorization?.let { a ->
            a.stageTrace.lastOrNull { it.outcome == "FAIL" }?.detail ?: "verweigert"
        } ?: "verweigert"

    private fun updateTaskForAction(actionId: String, newOutcome: StepOutcome) {
        val stepId = newOutcome.step.stepId
        for ((taskId, outcome) in outcomes) {
            if (outcome.steps.any { it.step.stepId == stepId }) {
                val updated = outcome.steps.map {
                    if (it.step.stepId == stepId) newOutcome else it
                }
                outcome.steps = updated
                if (updated.none { it.state == StepState.WAITING_HUMAN }) {
                    outcome.finished = true
                }
            }
        }
    }

    private fun pendingActionStep(pending: HumanGate.Pending): AgentStep =
        AgentStep(
            stepId = pending.action.actionId.substringAfterLast('-'),
            description = pending.action.requestedChange,
            capability = pending.action.capability,
            category = pending.action.category,
            risk = pending.result.finalRisk,
            adbCommand = null
        )

    /**
     * Rollenauflösung wird von außen injiziert (keine Zirkulärabhängigkeit zur Registry).
     * Fail-Closed-Default: Ohne Resolver wird jede Anfrage abgelehnt (GAST ist die
     * restriktivste Rolle und hat keine Projektrechte).
     */
    private var roleResolver: (String) -> de.d88.platform.model.Role = {
        throw IllegalStateException("RoleResolver nicht gesetzt – Fail-Closed")
    }

    fun setRoleResolver(resolver: (String) -> de.d88.platform.model.Role) {
        roleResolver = resolver
    }
}
