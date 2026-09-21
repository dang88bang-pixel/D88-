package de.d88.platform

import de.d88.platform.model.AuditEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanGateTest {

    private fun harness() = CoreHarness()

    @Test
    fun `approve removes from queue and audits with causal parent`() {
        val h = harness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            de.d88.platform.model.ActionRequest(
                actionId = "act-gate-1",
                deviceId = dev.deviceId,
                role = de.d88.platform.model.Role.CLIENT,
                projectId = "werkstatt",
                taskId = null,
                sessionId = sess.sessionId,
                capability = de.d88.platform.model.Capability.ADB,
                target = dev.deviceId,
                requestedChange = "Firmware",
                reason = "Test",
                dataFlow = de.d88.platform.model.DataFlow.LOCAL_ONLY,
                networkFlow = false,
                riskClass = de.d88.platform.model.RiskClass.CRITICAL,
                category = de.d88.platform.model.ActionCategory.FIRMWARE,
                evidence = null
            )
        )
        assertEquals(de.d88.platform.model.Decision.HUMAN_GATE_REQUIRED, result.decision)
        assertEquals(1, h.gate.pending().size)

        val decision = h.gate.approve("act-gate-1", approver = "admin")
        assertEquals(de.d88.platform.model.Decision.APPROVED, decision)
        assertEquals(0, h.gate.pending().size)

        val approvalEvent = h.audit.all.last { it.type == AuditEventType.HUMAN_APPROVAL }
        assertNotNull(approvalEvent.parentEventId)
        val requestEvent = h.audit.all.first { it.type == AuditEventType.HUMAN_REQUEST }
        assertEquals(requestEvent.eventId, approvalEvent.parentEventId)
    }

    @Test
    fun `deny audits with reason`() {
        val h = harness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        h.chain.evaluate(
            de.d88.platform.model.ActionRequest(
                actionId = "act-gate-2",
                deviceId = dev.deviceId,
                role = de.d88.platform.model.Role.CLIENT,
                projectId = "werkstatt",
                taskId = null,
                sessionId = sess.sessionId,
                capability = de.d88.platform.model.Capability.ADB,
                target = dev.deviceId,
                requestedChange = "Firmware",
                reason = "Test",
                dataFlow = de.d88.platform.model.DataFlow.LOCAL_ONLY,
                networkFlow = false,
                riskClass = de.d88.platform.model.RiskClass.CRITICAL,
                category = de.d88.platform.model.ActionCategory.FIRMWARE,
                evidence = null
            )
        )
        val decision = h.gate.deny("act-gate-2", approver = "admin", reason = "nicht jetzt")
        assertEquals(de.d88.platform.model.Decision.DENIED, decision)
        val denial = h.audit.all.last { it.type == AuditEventType.HUMAN_DENIAL }
        assertTrue(denial.result!!.contains("nicht jetzt"))
    }

    @Test
    fun `timeout defaults to DENY and audits HUMAN_TIMEOUT`() {
        val h = harness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        h.chain.evaluate(
            de.d88.platform.model.ActionRequest(
                actionId = "act-gate-3",
                deviceId = dev.deviceId,
                role = de.d88.platform.model.Role.CLIENT,
                projectId = "werkstatt",
                taskId = null,
                sessionId = sess.sessionId,
                capability = de.d88.platform.model.Capability.ADB,
                target = dev.deviceId,
                requestedChange = "Firmware",
                reason = "Test",
                dataFlow = de.d88.platform.model.DataFlow.LOCAL_ONLY,
                networkFlow = false,
                riskClass = de.d88.platform.model.RiskClass.CRITICAL,
                category = de.d88.platform.model.ActionCategory.FIRMWARE,
                evidence = null
            )
        )
        assertEquals(1, h.gate.pending().size)
        h.clock.t += de.d88.platform.core.HumanGate.DEFAULT_TTL_MS + 1000
        val expired = h.gate.processTimeouts()
        assertEquals(1, expired.size)
        assertEquals(0, h.gate.pending().size)
        assertTrue(h.audit.all.any { it.type == AuditEventType.HUMAN_TIMEOUT })
        assertNull("Timeout ist DENY", h.gate.approve("act-gate-3", "admin"))
    }

    @Test
    fun `unknown action ids are rejected`() {
        val h = harness()
        assertNull(h.gate.approve("act-unknown", "admin"))
        assertNull(h.gate.deny("act-unknown", "admin", "x"))
    }
}
