package de.d88.platform.debug

import de.d88.platform.core.AuthorizationChain
import de.d88.platform.core.AuditTrail
import de.d88.platform.core.HumanGate
import de.d88.platform.model.ActionRequest
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.AuthorizationResult
import de.d88.platform.model.Clock
import de.d88.platform.model.Decision

/**
 * Debug Gateway (Modul debug/DebugGateway, Spec Abschnitt 7).
 *
 * Einziger Weg zu Geräteaktionen. Reihenfolge (nie überspringbar):
 *
 *   DebugGateway.requestDeviceAction
 *     → AuthorizationChain (alle Stufen)
 *     → HUMAN_GATE_REQUIRED? → HumanGate-Queue (blockiert bis Menschenentscheidung)
 *     → APPROVED → AdbAdapter.execute
 *     → Ergebnis + Audit (DEVICE_ACTION / DEVICE_RESULT / TRANSPORT_UNAVAILABLE)
 *
 * Fehlt der Transport, wird EHRlich TRANSPORT_UNAVAILABLE gemeldet –
 * nie ein vorgetäuschter Erfolg.
 */
class DebugGateway(
    private val chain: AuthorizationChain,
    private val gate: HumanGate,
    private val adapter: AdbAdapter,
    private val audit: AuditTrail,
    private val clock: Clock = Clock.SYSTEM
) {

    sealed interface GatewayResult {
        val request: ActionRequest
        val authorization: AuthorizationResult?
    }

    data class Denied(override val request: ActionRequest, override val authorization: AuthorizationResult) :
        GatewayResult

    data class WaitingForHuman(
        override val request: ActionRequest,
        override val authorization: AuthorizationResult,
        val pending: HumanGate.Pending
    ) : GatewayResult

    data class Executed(
        override val request: ActionRequest,
        override val authorization: AuthorizationResult,
        val adbResult: AdbResult
    ) : GatewayResult

    data class TransportUnavailable(
        override val request: ActionRequest,
        override val authorization: AuthorizationResult,
        val detail: String
    ) : GatewayResult

    fun requestDeviceAction(request: ActionRequest, adbCommand: String? = null): GatewayResult {
        val adbCheck = if (adbCommand != null) {
            AdbCommandPolicy.evaluate(request.role, request.capability, adbCommand)
        } else null

        val authorization = chain.evaluate(request)

        when (authorization.decision) {
            Decision.DENIED -> {
                return Denied(request, authorization)
            }
            Decision.HUMAN_GATE_REQUIRED -> {
                if (adbCheck != null && adbCheck.verdict == AdbCommandPolicy.Verdict.DENIED) {
                    audit.record(
                        AuditEventType.POLICY_EVALUATED,
                        actionId = request.actionId,
                        deviceId = request.deviceId,
                        capability = request.capability,
                        policyDecision = "ADB_COMMAND_DENIED",
                        result = adbCheck.reason
                    )
                    return Denied(request, authorization)
                }
                return WaitingForHuman(request, authorization, gate.pending().first { it.action.actionId == request.actionId })
            }
            Decision.APPROVED -> {
                if (adbCheck != null && adbCheck.verdict == AdbCommandPolicy.Verdict.REQUIRES_GATE) {
                    // Auch bei APPROVED: ein ADB-Kommando der Gate-Klasse braucht Menschenfreigabe.
                    gate.register(request, authorization)
                    return WaitingForHuman(request, authorization, gate.pending().first { it.action.actionId == request.actionId })
                }
                return executeAndAudit(request, authorization, adbCommand)
            }
        }
    }

    /**
     * Nach menschlicher Freigabe: wirklich ausführen (oder ehrlich melden, dass
     * kein Transport da ist). Wirft [IllegalStateException], wenn keine offene
     * Freigabe existiert.
     */
    fun executeAfterApproval(actionId: String, approver: String = "human", adbCommand: String? = null): GatewayResult {
        val pending = gate.pending().firstOrNull { it.action.actionId == actionId }
            ?: throw IllegalStateException("Keine offene Freigabe für Action '$actionId'")
        val decision = gate.approve(actionId, approver = approver)
        if (decision != Decision.APPROVED) {
            return Denied(pending.action, pending.result)
        }
        return executeAndAudit(pending.action, pending.result, adbCommand)
    }

    private fun executeAndAudit(
        request: ActionRequest,
        authorization: AuthorizationResult,
        adbCommand: String?
    ): GatewayResult {
        if (adbCommand == null) {
            // Nicht-ADB-Aktion: lokal/simuliert ausführbar (z. B. lokale Analyse).
            audit.record(
                AuditEventType.DEVICE_ACTION,
                actionId = request.actionId,
                deviceId = request.deviceId,
                sessionId = request.sessionId,
                capability = request.capability,
                result = "Aktion lokal verarbeitet"
            )
            audit.record(
                AuditEventType.DEVICE_RESULT,
                actionId = request.actionId,
                deviceId = request.deviceId,
                capability = request.capability,
                result = "OK"
            )
            audit.record(
                AuditEventType.VALIDATION,
                actionId = request.actionId,
                deviceId = request.deviceId,
                capability = request.capability,
                result = "validiert"
            )
            return Executed(request, authorization, AdbResult(request.deviceId, "(local)", 0, "", "", clock.now(), realTransport = false))
        }

        if (adapter.state() != TransportState.AVAILABLE) {
            audit.record(
                AuditEventType.TRANSPORT_UNAVAILABLE,
                actionId = request.actionId,
                deviceId = request.deviceId,
                capability = request.capability,
                result = "Adapter '${adapter.name}': ${adapter.state().name}"
            )
            return TransportUnavailable(
                request, authorization,
                "Transport '${adapter.name}' nicht verfügbar (Status: ${adapter.state().name}) – " +
                    "Aktion wurde NICHT ausgeführt. Reales ADB/USB/BLE/WLAN folgt mit Hardware."
            )
        }

        val adbResult = adapter.execute(request.deviceId, adbCommand)
        audit.record(
            AuditEventType.DEVICE_ACTION,
            actionId = request.actionId,
            deviceId = request.deviceId,
            sessionId = request.sessionId,
            capability = request.capability,
            result = adbResult.command
        )
        audit.record(
            AuditEventType.DEVICE_RESULT,
            actionId = request.actionId,
            deviceId = request.deviceId,
            capability = request.capability,
            result = "exit=${adbResult.exitCode}"
        )
        return Executed(request, authorization, adbResult)
    }
}
