package de.d88.platform.core

import de.d88.platform.device.DeviceRegistry
import de.d88.platform.model.ActionRequest
import de.d88.platform.model.AgentSession
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.AuthorizationResult
import de.d88.platform.model.Clock
import de.d88.platform.model.Decision
import de.d88.platform.model.Policy
import de.d88.platform.model.Role
import de.d88.platform.model.Stage
import de.d88.platform.model.StageOutcome
import de.d88.platform.model.TrustState

/**
 * Die D88-Autorisierungskette (Spec README.md, Abschnitt 18):
 *
 *  DEVICE → IDENTITY → TRUST → ROLE → PROJECT → TASK → SESSION →
 *  CAPABILITY → POLICY → HUMAN GATE → [EXECUTION → VALIDATION → AUDIT]
 *
 * Grundregel: Der Agent darf niemals eine Ebene überspringen.
 * Die Kette läuft immer in fester Reihenfolge durch. Scheitert eine Stufe,
 * endet sie mit DENIED an genau dieser Stufe (keine impliziten Umwege).
 *
 * EXECUTION/VALIDATION werden vom Executor (DebugGateway/AgentRuntime)
 * ausgeführt; AUDIT wird durch [AuditTrail] in jedem Fall mitgeschrieben.
 */
class AuthorizationChain(
    private val registry: DeviceRegistry,
    private val policy: Policy = Policy.CURRENT,
    private val policyEngine: PolicyEngine = PolicyEngine(policy),
    private val gate: HumanGate,
    private val audit: AuditTrail,
    private val clock: Clock = Clock.SYSTEM
) {

    /**
     * Bewertet eine Aktion vollständig.
     * Das Ergebnis enthält den kompletten Stufen-Trace – nachvollziehbar für
     * Human Gate, Audit und UI.
     */
    fun evaluate(request: ActionRequest): AuthorizationResult {
        val trace = mutableListOf<StageOutcome>()
        val result: AuthorizationResult

        result = runCatching {
            val device = evaluateDevice(request, trace) ?: return@runCatching finish(
                request, Decision.DENIED, Stage.DEVICE, trace, null
            )

            if (!evaluateIdentity(device, trace)) {
                return@runCatching finish(request, Decision.DENIED, Stage.IDENTITY, trace, device)
            }
            if (!evaluateTrust(device, trace)) {
                return@runCatching finish(request, Decision.DENIED, Stage.TRUST, trace, device)
            }
            val roleViolation = evaluateRole(device, trace)
            if (roleViolation != null) {
                return@runCatching finish(request, Decision.DENIED, Stage.ROLE, trace, device)
            }
            val projectViolation = evaluateProject(device, request, trace)
            if (projectViolation != null) {
                return@runCatching finish(request, Decision.DENIED, Stage.PROJECT, trace, device)
            }
            val taskViolation = evaluateTask(device, request, trace)
            if (taskViolation != null) {
                return@runCatching finish(request, Decision.DENIED, Stage.TASK, trace, device)
            }
            val session = evaluateSession(request, trace)
            if (session == null) {
                return@runCatching finish(request, Decision.DENIED, Stage.SESSION, trace, device)
            }
            val capabilityViolation = evaluateCapability(device, session, request, trace)
            if (capabilityViolation != null) {
                return@runCatching finish(request, Decision.DENIED, Stage.CAPABILITY, trace, device)
            }
            val policyEval = policyEngine.evaluate(request, device, session)
            trace += StageOutcome(
                Stage.POLICY,
                if (policyEval.violations.isEmpty()) "PASS" else "FAIL",
                if (policyEval.violations.isEmpty()) {
                    "Risiko ${policyEval.finalRisk.name}; " +
                        policyEval.notes.joinToString("; ")
                } else {
                    policyEval.violations.joinToString("; ")
                }
            )
            if (policyEval.violations.isNotEmpty()) {
                return@runCatching finish(request, Decision.DENIED, Stage.POLICY, trace, device)
            }

            if (policyEval.requiresHumanGate) {
                trace += StageOutcome(
                    Stage.HUMAN_GATE, "REQUIRED",
                    "Risiko ${policyEval.finalRisk.name} erfordert menschliche Freigabe (Human Gate)"
                )
                val gateResult = finish(request, Decision.HUMAN_GATE_REQUIRED, Stage.HUMAN_GATE, trace, device)
                gate.register(request, gateResult)
                return@runCatching gateResult
            }

            trace += StageOutcome(Stage.HUMAN_GATE, "PASS", "Keine Freigabe erforderlich (Risiko ${policyEval.finalRisk.name})")
            finish(request, Decision.APPROVED, Stage.HUMAN_GATE, trace, device)
        }.getOrNull() ?: denyUnexpected(request, trace)

        return result
    }

    // ------------------------------------------------------------------ Stufen

    private fun evaluateDevice(request: ActionRequest, trace: MutableList<StageOutcome>): de.d88.platform.model.DeviceIdentity? {
        val device = registry.find(request.deviceId)
        trace += StageOutcome(
            Stage.DEVICE,
            if (device != null) "FOUND" else "FAIL",
            device?.let { "Gerät '${it.deviceId}' gefunden (Rolle ${it.role}, Trust ${it.trustState})" }
                ?: "Unbekanntes Gerät '${request.deviceId}' – es existiert keine D88-Identität"
        )
        return device
    }

    private fun evaluateIdentity(device: de.d88.platform.model.DeviceIdentity, trace: MutableList<StageOutcome>): Boolean {
        val problems = mutableListOf<String>()
        if (device.deviceKey.isBlank()) problems += "device_key fehlt"
        if (device.d88Version.isBlank()) problems += "d88_version fehlt"
        if (device.policyVersion <= 0) problems += "policy_version fehlt"
        if (device.policyVersion != policy.version) {
            problems += "WARN: Policy-Version des Geräts (${device.policyVersion}) ≠ aktueller Policy (${policy.version}) – Neupairing empfohlen"
        }
        val failed = problems.any { !it.startsWith("WARN") }
        trace += StageOutcome(
            Stage.IDENTITY,
            if (failed) "FAIL" else "PASS",
            if (problems.isEmpty()) "Identität konsistent" else problems.joinToString("; ")
        )
        return !failed
    }

    private fun evaluateTrust(device: de.d88.platform.model.DeviceIdentity, trace: MutableList<StageOutcome>): Boolean {
        val requiresTrusted = device.role.requiresTrustedState
        val ok = TrustStateMachine.authorizable(device.trustState, requiresTrusted)
        val detail = when {
            device.trustState.isDead ->
                "Trust-Zustand ${device.trustState.name} ist endgültig – keine Autorisierung möglich"
            ok -> when (device.role) {
                Role.GAST -> "GAST im erlaubten Gast-Zustand ${device.trustState.name} (nie TRUSTED-fähig)"
                else -> "Gerät TRUSTED"
            }
            else -> "Trust-Zustand ${device.trustState.name} reicht für Rolle ${device.role} nicht aus " +
                ("(verlangt: " + if (requiresTrusted) "TRUSTED" else "Gast-Zustandsraum") + ")"
        }
        trace += StageOutcome(Stage.TRUST, if (ok) "PASS" else "FAIL", detail)
        return ok
    }

    private fun evaluateRole(device: de.d88.platform.model.DeviceIdentity, trace: MutableList<StageOutcome>): String? {
        val violation = when (device.role) {
            Role.CLIENT -> if (device.projectScopes.isEmpty()) {
                "CLIENT ohne Projekt-Scope – Rolle erlaubt keine projektlosen Vollzugriffe"
            } else null
            else -> null
        }
        trace += StageOutcome(
            Stage.ROLE,
            if (violation == null) "PASS" else "FAIL",
            if (violation == null) {
                "Rolle ${device.role.name}: ${RoleEngine.baseline(device.role).first()} (Basis)"
            } else violation
        )
        return violation
    }

    private fun evaluateProject(device: de.d88.platform.model.DeviceIdentity, request: ActionRequest, trace: MutableList<StageOutcome>): String? {
        // Projektstufe = geräteseitig: Rolle + Project-Scope.
        // (Die Session-Übereinstimmung wird in der SESSION-Stage geprüft.)
        val violation = when (device.role) {
            Role.GAST -> if (request.projectId != null) {
                "GAST hat keinen Zugriff auf Projekte (Projekt: ${request.projectId})"
            } else null
            Role.CLIENT -> when {
                request.projectId == null -> "CLIENT benötigt eine Projektbindung"
                request.projectId !in device.projectScopes ->
                    "Projekt '${request.projectId}' nicht im Project-Scope des Geräts " +
                        "(erlaubt: ${device.projectScopes.joinToString()})"
                else -> null
            }
            Role.ADMIN -> if (request.projectId != null &&
                device.projectScopes.isNotEmpty() &&
                request.projectId !in device.projectScopes
            ) {
                "Projekt '${request.projectId}' nicht im Project-Scope des Admin-Geräts"
            } else null
        }
        trace += StageOutcome(
            Stage.PROJECT,
            if (violation == null) "PASS" else "FAIL",
            if (violation == null) {
                "Projektbindung geprüft: ${request.projectId ?: "(keine – erlaubt für ${device.role})"}"
            } else violation
        )
        return violation
    }

    private fun evaluateTask(device: de.d88.platform.model.DeviceIdentity, request: ActionRequest, trace: MutableList<StageOutcome>): String? {
        // Aufgabenstufe = geräteseitig: Task-Scope (eigene Stufe, keine Überspringung).
        val violation = when {
            device.role == Role.GAST && request.taskId != null ->
                "GAST hat keinen Zugriff auf Aufgaben (Task: ${request.taskId})"
            request.taskId != null && device.taskScopes.isNotEmpty() &&
                request.taskId !in device.taskScopes ->
                "Aufgabe '${request.taskId}' nicht im Task-Scope des Geräts"
            else -> null
        }
        trace += StageOutcome(
            Stage.TASK,
            if (violation == null) "PASS" else "FAIL",
            if (violation == null) "Aufgabenbindung geprüft: ${request.taskId ?: "(keine)"}" else violation
        )
        return violation
    }

    private fun evaluateSession(request: ActionRequest, trace: MutableList<StageOutcome>): AgentSession? {
        val sessionId = request.sessionId
        if (sessionId == null) {
            trace += StageOutcome(Stage.SESSION, "FAIL", "Agent-Aktion ohne Session – D88 verlangt einen Session-Security-Context")
            return null
        }
        val session = registry.findSession(sessionId)
        if (session == null) {
            trace += StageOutcome(Stage.SESSION, "FAIL", "Session '$sessionId' existiert nicht oder wurde beendet")
            return null
        }
        val violations = mutableListOf<String>()
        if (!session.isGranted(clock.now())) {
            violations += "Session-Grant abgelaufen oder nicht aktiv (authorization=${session.authorizationState})"
        }
        if (session.deviceId != request.deviceId) {
            violations += "Session gehört zu anderem Gerät (${session.deviceId})"
        }
        if (session.projectId != request.projectId) {
            violations += "Projekt des Requests '${request.projectId}' weicht von der Session-Bindung '${session.projectId}' ab"
        }
        if (violations.isEmpty()) {
            trace += StageOutcome(Stage.SESSION, "PASS", "Session '${session.sessionId}' aktiv, Grant gültig")
            return session
        }
        trace += StageOutcome(Stage.SESSION, "FAIL", violations.joinToString("; "))
        return null
    }

    private fun evaluateCapability(
        device: de.d88.platform.model.DeviceIdentity,
        session: AgentSession,
        request: ActionRequest,
        trace: MutableList<StageOutcome>
    ): String? {
        val violation = CapabilityEngine.check(
            device, session, request.capability, policy.guestDeniedCapabilities
        )
        trace += StageOutcome(
            Stage.CAPABILITY,
            if (violation == null) "PASS" else "FAIL",
            if (violation == null) "Capability ${request.capability} wirksam (Gerät ∩ Session ∩ Policy)"
            else violation
        )
        return violation
    }

    // ------------------------------------------------------------------ Abschluss

    private fun finish(
        request: ActionRequest,
        decision: Decision,
        stoppedAt: Stage,
        trace: List<StageOutcome>,
        device: de.d88.platform.model.DeviceIdentity?
    ): AuthorizationResult {
        val result = AuthorizationResult(
            actionId = request.actionId,
            decision = decision,
            finalRisk = policyEngine.escalateRisk(request),
            stoppedAt = stoppedAt,
            stageTrace = trace,
            policyVersion = policy.version,
            deviceId = request.deviceId,
            decidedAt = clock.now()
        )
        // AUDIT-Stage: immer, bei jeder Entscheidung.
        audit.record(
            type = AuditEventType.POLICY_EVALUATED,
            actionId = request.actionId,
            deviceId = request.deviceId,
            sessionId = request.sessionId,
            projectId = request.projectId,
            taskId = request.taskId,
            capability = request.capability,
            policyDecision = "${decision.name}@${stoppedAt.name}",
            authorization = result.finalRisk.name,
            result = "Stufen: " + result.stageTrace.size,
            evidence = result.reasons.joinToString(" | ")
        )
        return result
    }

    private fun denyUnexpected(
        request: ActionRequest,
        trace: MutableList<StageOutcome>
    ): AuthorizationResult {
        trace += StageOutcome(Stage.POLICY, "FAIL", "Unerwarteter Fehler in der Kette – Fail-Closed")
        return finish(request, Decision.DENIED, Stage.POLICY, trace, null)
    }
}
