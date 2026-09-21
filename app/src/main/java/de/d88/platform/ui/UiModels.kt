package de.d88.platform.ui

import de.d88.platform.agent.AgentRuntime
import de.d88.platform.model.AuditEvent
import de.d88.platform.model.AuthorizationResult
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.Role
import de.d88.platform.model.TrustState

/** Flache, renderbare UI-Modelle (getrennt vom Domänenmodell). */

data class UiDevice(
    val deviceId: String,
    val deviceType: String,
    val platformVersion: String,
    val role: Role,
    val trustState: TrustState,
    val simulated: Boolean,
    val capabilities: List<String>,
    val projectScopes: List<String>,
    val activeSessions: Int,
    val trustLabel: String,
    val trustColor: Int,
    val roleColor: Int
) {
    val headline: String
        get() = "$deviceType · $deviceId"

    companion object {
        fun from(d: DeviceIdentity, sessions: Int): UiDevice = UiDevice(
            deviceId = d.deviceId,
            deviceType = d.deviceType,
            platformVersion = d.platformVersion,
            role = d.role,
            trustState = d.trustState,
            simulated = d.simulated,
            capabilities = d.capabilities.map { it.name },
            projectScopes = d.projectScopes.toList(),
            activeSessions = sessions,
            trustLabel = trustLabelFor(d.trustState),
            trustColor = trustColorFor(d.trustState),
            roleColor = roleColorFor(d.role)
        )

        fun trustLabelFor(state: TrustState): String = when (state) {
            TrustState.TRUSTED -> "TRUSTED"
            TrustState.TRUST_PENDING -> "Vertrauen: eingeschränkt"
            TrustState.SUSPENDED -> "Suspendiert"
            TrustState.REVOKED -> "Widerrufen"
            TrustState.EXPIRED -> "Abgelaufen"
            TrustState.BLOCKED -> "Gesperrt"
            else -> state.name
        }

        fun trustColorFor(state: TrustState): Int = when (state) {
            TrustState.TRUSTED -> 0xFF2E7D32.toInt()
            TrustState.TRUST_PENDING, TrustState.SUSPENDED, TrustState.EXPIRED -> 0xFFF9A825.toInt()
            else -> 0xFFC62828.toInt()
        }

        fun roleColorFor(role: Role): Int = when (role) {
            Role.ADMIN -> 0xFF6A1B9A.toInt()
            Role.CLIENT -> 0xFF0277BD.toInt()
            Role.GAST -> 0xFF546E7A.toInt()
        }
    }
}

data class UiApproval(
    val actionId: String,
    val deviceId: String,
    val role: Role,
    val projectId: String?,
    val taskId: String?,
    val action: String,
    val data: String,
    val network: Boolean,
    val change: String,
    val risk: String,
    val expiresAt: Long
)

data class UiAudit(
    val eventId: String,
    val timestamp: Long,
    val type: String,
    val deviceId: String?,
    val result: String?,
    val hash: String
) {
    companion object {
        fun from(e: AuditEvent): UiAudit = UiAudit(
            e.eventId, e.timestamp, e.type.name, e.deviceId, e.result, e.hash
        )
    }
}

data class UiStep(
    val stepId: String,
    val description: String,
    val state: String,
    val detail: String
)

data class UiTask(
    val taskId: String,
    val deviceId: String,
    val intent: String,
    val steps: List<UiStep>,
    val finished: Boolean
) {
    companion object {
        fun from(outcome: AgentRuntime.TaskOutcome): UiTask = UiTask(
            taskId = outcome.task.taskId,
            deviceId = outcome.task.deviceId,
            intent = outcome.task.intent,
            steps = outcome.steps.map {
                UiStep(it.step.stepId, it.step.description, it.state.name, it.detail)
            },
            finished = outcome.finished
        )
    }
}

data class UiStats(
    val deviceCount: Int,
    val adminCount: Int,
    val clientCount: Int,
    val guestCount: Int,
    val pendingApprovals: Int,
    val auditCount: Int,
    val activeSessions: Int,
    val policyVersion: Int,
    val networkDefault: String,
    val transportState: String,
    val simulation: Boolean
)

data class ChatLine(
    val id: String,
    val who: Who,
    val text: String,
    val time: Long
) {
    enum class Who { USER, D88, GATE }
}
