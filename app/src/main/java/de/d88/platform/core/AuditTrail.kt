package de.d88.platform.core

import de.d88.platform.model.AuditEvent
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.Capability
import de.d88.platform.model.Clock
import java.security.MessageDigest

/**
 * Causal Audit (Spec README.md, Abschnitt 14; Modul audit/).
 *
 * Jeder relevante Vorgang wird kausal verknüpft (parentEventId) und kryptografisch
 * signiert: hash = SHA-256(parentHash | type | actionId | timestamp | result | evidence).
 * Eine manipulierte Stelle bricht die Kette und ist dadurch nachweisbar.
 */
class AuditTrail(private val clock: Clock = Clock.SYSTEM) {

    private val events = mutableListOf<AuditEvent>()
    private var sequence = 0L

    @Synchronized
    fun record(
        type: AuditEventType,
        actionId: String? = null,
        deviceId: String? = null,
        sessionId: String? = null,
        projectId: String? = null,
        taskId: String? = null,
        capability: Capability? = null,
        policyDecision: String? = null,
        authorization: String? = null,
        parentEventId: String? = null,
        result: String? = null,
        evidence: String? = null
    ): AuditEvent {
        val parent = parentEventId?.let { id -> events.firstOrNull { it.eventId == id } }
        val parentHash = parent?.hash
        val timestamp = clock.now()
        val seq = ++sequence
        val eventId = "evt-${seq.toString().padStart(6, '0')}"
        val hash = hashChain(
            parentHash, type.name, actionId, timestamp, result, evidence
        )
        val event = AuditEvent(
            eventId = eventId,
            type = type,
            actionId = actionId,
            deviceId = deviceId,
            sessionId = sessionId,
            projectId = projectId,
            taskId = taskId,
            capability = capability,
            policyDecision = policyDecision,
            authorization = authorization,
            parentEventId = parentEventId,
            timestamp = timestamp,
            result = result,
            evidence = evidence,
            hash = hash,
            parentHash = parentHash
        )
        events += event
        return event
    }

    /**
     * Stellt Events aus einer Persistenz wieder her (Reihenfolge bleibt erhalten,
     * die Zählung fährt über die wiederhergestellten IDs fort).
     */
    @Synchronized
    fun restore(events: List<AuditEvent>) {
        if (this.events.isNotEmpty()) return
        this.events += events
        var maxSeq = 0L
        for (e in events) {
            val seq = e.eventId.removePrefix("evt-").toLongOrNull() ?: 0L
            if (seq > maxSeq) maxSeq = seq
        }
        sequence = maxSeq
    }

    /**
     * Verifiziert die gesamte Hash-Kette.
     * Rückgabe: (ok, fehlerhafterEventId?)
     */
    fun verifyChain(): Pair<Boolean, String?> {
        var expectedParentHash: String? = null
        for (event in events) {
            if (event.parentHash != expectedParentHash) {
                return false to event.eventId
            }
            val recomputed = hashChain(
                event.parentHash, event.type.name, event.actionId,
                event.timestamp, event.result, event.evidence
            )
            if (recomputed != event.hash) {
                return false to event.eventId
            }
            expectedParentHash = event.hash
        }
        return true to null
    }

    fun forDevice(deviceId: String): List<AuditEvent> =
        events.filter { it.deviceId == deviceId }

    fun forAction(actionId: String): List<AuditEvent> =
        events.filter { it.actionId == actionId }

    fun tail(n: Int): List<AuditEvent> = events.takeLast(n)

    val count: Int
        get() = events.size

    val all: List<AuditEvent>
        get() = events.toList()

    companion object {
        fun hashChain(
            parentHash: String?,
            type: String,
            actionId: String?,
            timestamp: Long,
            result: String?,
            evidence: String?
        ): String {
            val payload = (parentHash ?: "GENESIS") + "|" + type + "|" +
                (actionId ?: "-") + "|" + timestamp + "|" +
                (result ?: "-") + "|" + (evidence ?: "-")
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
