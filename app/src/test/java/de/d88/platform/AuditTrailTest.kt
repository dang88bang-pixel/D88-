package de.d88.platform

import de.d88.platform.core.AuditTrail
import de.d88.platform.model.AuditEvent
import de.d88.platform.model.AuditEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditTrailTest {

    /** Frische Trail, die mit den gegebenen Events befüllt wird (für Tampering-Tests). */
    private fun freshTrailWith(events: List<AuditEvent>): Pair<Boolean, String?> {
        val fresh = AuditTrail(CoreHarness().clock)
        fresh.restore(events)
        return fresh.verifyChain()
    }

    @Test
    fun `chain verifies when intact`() {
        val h = CoreHarness()
        val e1 = h.audit.record(AuditEventType.USER_REQUEST, actionId = "a1", result = "r1")
        val e2 = h.audit.record(
            AuditEventType.TASK_CREATED, actionId = "a1",
            parentEventId = e1.eventId, result = "r2"
        )
        assertEquals(e1.hash, e2.parentHash)
        val (ok, broken) = h.audit.verifyChain()
        assertTrue(ok)
        assertEquals(null, broken)
    }

    @Test
    fun `tampered hash is detected`() {
        val h = CoreHarness()
        val e1 = h.audit.record(AuditEventType.USER_REQUEST, actionId = "a1", result = "r1")
        val e2 = h.audit.record(
            AuditEventType.TASK_CREATED, actionId = "a1",
            parentEventId = e1.eventId, result = "r2"
        )
        val tampered = listOf(e1, e2.copy(hash = "0".repeat(64)))
        val (ok, broken) = freshTrailWith(tampered)
        assertFalse(ok)
        assertEquals(e2.eventId, broken)
    }

    @Test
    fun `broken parent link is detected`() {
        val h = CoreHarness()
        val e1 = h.audit.record(AuditEventType.USER_REQUEST, actionId = "a1", result = "r1")
        val e2 = h.audit.record(
            AuditEventType.TASK_CREATED, actionId = "a1",
            parentEventId = e1.eventId, result = "r2"
        )
        val fakeParent = "f".repeat(64)
        val corrupted = listOf(
            e1,
            e2.copy(
                parentHash = fakeParent,
                hash = AuditTrail.hashChain(
                    fakeParent, e2.type.name, e2.actionId, e2.timestamp, e2.result, e2.evidence
                )
            )
        )
        val (ok, broken) = freshTrailWith(corrupted)
        assertFalse(ok)
        assertEquals(e2.eventId, broken)
    }

    @Test
    fun `causal query by device and action`() {
        val h = CoreHarness()
        h.audit.record(AuditEventType.USER_REQUEST, actionId = "a1", deviceId = "dev-x")
        h.audit.record(AuditEventType.USER_REQUEST, actionId = "a2", deviceId = "dev-y")
        assertEquals(1, h.audit.forDevice("dev-x").size)
        assertEquals(1, h.audit.forAction("a2").size)
    }

    @Test
    fun `restore continues the sequence counter`() {
        val h = CoreHarness()
        val e1 = h.audit.record(AuditEventType.USER_REQUEST, result = "r")
        val e2 = h.audit.record(AuditEventType.TASK_CREATED, result = "r2")
        val h2 = CoreHarness()
        h2.audit.restore(listOf(e1, e2))
        val e3 = h2.audit.record(AuditEventType.TASK_CREATED, result = "r3")
        assertEquals("evt-000003", e3.eventId)
    }
}
