package de.d88.platform.core

import de.d88.platform.model.ActionRequest
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.AuthorizationResult
import de.d88.platform.model.Clock
import de.d88.platform.model.Decision
import de.d88.platform.model.Stage

/**
 * Human-in-the-Loop (Spec README.md, Abschnitt 13; Modul authorization/HumanGate).
 *
 * Zentrale Umsetzung: Jede kritische ( bzw. policypflichtige ) Aktion wird in eine
 * Freigabe-Queue gelegt und blockiert, bis ein Mensch entscheidet.
 *
 * Timeout-Default ist DENY (sichere Seite): Wer nicht rechtzeitig entscheidet,
 * verhindert die Ausführung.
 */
class HumanGate(
    private val audit: AuditTrail,
    private val clock: Clock = Clock.SYSTEM,
    private val defaultTtlMs: Long = DEFAULT_TTL_MS
) {

    data class Pending(
        val action: ActionRequest,
        val result: AuthorizationResult,
        val expiresAt: Long,
        val createdEventId: String
    ) {
        fun isExpired(now: Long): Boolean = now > expiresAt
    }

    private val queue = ArrayDeque<Pending>()

    /** Legt eine Freigabe-Anfrage in die Queue und auditiert sie. */
    @Synchronized
    fun register(action: ActionRequest, result: AuthorizationResult): Pending {
        val event = audit.record(
            type = AuditEventType.HUMAN_REQUEST,
            actionId = action.actionId,
            deviceId = action.deviceId,
            sessionId = action.sessionId,
            projectId = action.projectId,
            taskId = action.taskId,
            capability = action.capability,
            policyDecision = result.finalRisk.name,
            authorization = "GATE_REQUIRED",
            result = "Warteschlange"
        )
        val pending = Pending(action, result, clock.now() + defaultTtlMs, event.eventId)
        queue += pending
        return pending
    }

    /** Menschenentscheidung: FREIGEBEN. */
    @Synchronized
    fun approve(actionId: String, approver: String): Decision? {
        val pending = queue.firstOrNull { it.action.actionId == actionId } ?: return null
        queue.remove(pending)
        audit.record(
            type = AuditEventType.HUMAN_APPROVAL,
            actionId = actionId,
            deviceId = pending.action.deviceId,
            sessionId = pending.action.sessionId,
            projectId = pending.action.projectId,
            taskId = pending.action.taskId,
            capability = pending.action.capability,
            authorization = "APPROVED_BY:$approver",
            parentEventId = pending.createdEventId,
            result = "Freigegeben"
        )
        return Decision.APPROVED
    }

    /** Menschenentscheidung: ABLEHNEN. */
    @Synchronized
    fun deny(actionId: String, approver: String, reason: String): Decision? {
        val pending = queue.firstOrNull { it.action.actionId == actionId } ?: return null
        queue.remove(pending)
        audit.record(
            type = AuditEventType.HUMAN_DENIAL,
            actionId = actionId,
            deviceId = pending.action.deviceId,
            sessionId = pending.action.sessionId,
            projectId = pending.action.projectId,
            taskId = pending.action.taskId,
            capability = pending.action.capability,
            authorization = "DENIED_BY:$approver",
            parentEventId = pending.createdEventId,
            result = "Abgelehnt: $reason"
        )
        return Decision.DENIED
    }

    /** Verarbeitet abgelaufene Freigaben (Default: DENY) und gibt sie zurück. */
    @Synchronized
    fun processTimeouts(now: Long = clock.now()): List<Pending> {
        val expired = queue.filter { it.isExpired(now) }
        for (pending in expired) {
            queue.remove(pending)
            audit.record(
                type = AuditEventType.HUMAN_TIMEOUT,
                actionId = pending.action.actionId,
                deviceId = pending.action.deviceId,
                sessionId = pending.action.sessionId,
                capability = pending.action.capability,
                authorization = "TIMEOUT_DENY",
                parentEventId = pending.createdEventId,
                result = "Freigabe abgelaufen → abgelehnt"
            )
        }
        return expired
    }

    /** Offenstehende (nicht abgelaufene) Freigaben. */
    @Synchronized
    fun pending(now: Long = clock.now()): List<Pending> =
        queue.filterNot { it.isExpired(now) }

    val pendingCount: Int
        @Synchronized
        get() = queue.size

    companion object {
        const val DEFAULT_TTL_MS: Long = 5 * 60 * 1000L
    }
}
