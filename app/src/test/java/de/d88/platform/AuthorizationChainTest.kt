package de.d88.platform

import de.d88.platform.model.ActionCategory
import de.d88.platform.model.ActionRequest
import de.d88.platform.model.Capability
import de.d88.platform.model.DataFlow
import de.d88.platform.model.Decision
import de.d88.platform.model.NetworkState
import de.d88.platform.model.RiskClass
import de.d88.platform.model.Role
import de.d88.platform.model.Stage
import de.d88.platform.model.TrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests der 13-stufigen Autorisierungskette:
 * DEVICE → IDENTITY → TRUST → ROLE → PROJECT → TASK → SESSION →
 * CAPABILITY → POLICY → HUMAN GATE → EXECUTION → VALIDATION → AUDIT
 * (EXECUTION/VALIDATION laufen im Executor; AUDIT in jedem Fall).
 */
class AuthorizationChainTest {

    private fun CoreHarness.action(
        deviceId: String,
        sessionId: String?,
        capability: Capability,
        category: ActionCategory = ActionCategory.READ,
        risk: RiskClass = RiskClass.LOW,
        project: String? = null,
        networkFlow: Boolean = false,
        dataFlow: DataFlow = DataFlow.LOCAL_ONLY,
        role: Role = Role.CLIENT
    ) = ActionRequest(
        actionId = "act-${capability}-${System.nanoTime() % 100000}",
        deviceId = deviceId,
        role = role,
        projectId = project,
        taskId = null,
        sessionId = sessionId,
        capability = capability,
        target = deviceId,
        requestedChange = "Testaktion",
        reason = "Test",
        dataFlow = dataFlow,
        networkFlow = networkFlow,
        riskClass = risk,
        category = category,
        evidence = null
    )

    @Test
    fun `unknown device is denied at DEVICE stage`() {
        val h = CoreHarness()
        val result = h.chain.evaluate(h.action("dev-unknown", "sess-x", Capability.DEVICE_INFO))
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.DEVICE, result.stoppedAt)
    }

    @Test
    fun `request without session is denied at SESSION stage`() {
        val h = CoreHarness()
        val dev = h.client()
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sessionId = null, capability = Capability.FILE_READ, project = "werkstatt")
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.SESSION, result.stoppedAt)
    }

    @Test
    fun `expired session grant is denied at SESSION stage`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt", ttlMs = 1000)
        h.clock.t += 2000 // TTL abgelaufen
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt")
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.SESSION, result.stoppedAt)
    }

    @Test
    fun `CLIENT without TRUSTED is denied at TRUST stage`() {
        val h = CoreHarness()
        val dev = h.client(trust = TrustState.TRUST_PENDING)
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt")
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.TRUST, result.stoppedAt)
    }

    @Test
    fun `BLOCKED device is denied at TRUST stage`() {
        val h = CoreHarness()
        val dev = h.client(trust = TrustState.BLOCKED)
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt")
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.TRUST, result.stoppedAt)
    }

    @Test
    fun `CLIENT reading own project data is approved`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt")
        )
        assertEquals(Decision.APPROVED, result.decision)
    }

    @Test
    fun `CLIENT reading foreign project is denied at PROJECT stage`() {
        val h = CoreHarness()
        val dev = h.client(project = "werkstatt")
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "privat-b")
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.PROJECT, result.stoppedAt)
    }

    @Test
    fun `CLIENT without project binding is denied at PROJECT stage`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = null)
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.PROJECT, result.stoppedAt)
    }

    @Test
    fun `CLIENT firmware update requires human gate`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(
                dev.deviceId, sess.sessionId, Capability.ADB,
                category = ActionCategory.FIRMWARE, risk = RiskClass.CRITICAL,
                project = "werkstatt"
            )
        )
        assertEquals(Decision.HUMAN_GATE_REQUIRED, result.decision)
        assertEquals(Stage.HUMAN_GATE, result.stoppedAt)
        assertEquals(1, h.gate.pending().size)
    }

    @Test
    fun `ADMIN firmware update also requires human gate (never automatic)`() {
        val h = CoreHarness()
        val dev = h.admin()
        val sess = h.session(dev)
        val result = h.chain.evaluate(
            h.action(
                dev.deviceId, sess.sessionId, Capability.ADB,
                category = ActionCategory.FIRMWARE, risk = RiskClass.CRITICAL,
                role = Role.ADMIN
            )
        )
        assertEquals(Decision.HUMAN_GATE_REQUIRED, result.decision)
        assertEquals(1, h.gate.pending().size)
    }

    @Test
    fun `ADMIN reading device info is approved without gate`() {
        val h = CoreHarness()
        val dev = h.admin()
        val sess = h.session(dev)
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.DEVICE_INFO, role = Role.ADMIN)
        )
        assertEquals(Decision.APPROVED, result.decision)
        assertEquals(0, h.gate.pending().size)
    }

    @Test
    fun `GAST reading own guest files is approved`() {
        val h = CoreHarness()
        val dev = h.gast()
        val sess = h.session(dev)
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, role = Role.GAST)
        )
        assertEquals(Decision.APPROVED, result.decision)
    }

    @Test
    fun `GAST app install is denied at CAPABILITY stage`() {
        val h = CoreHarness()
        val dev = h.gast()
        val sess = h.session(dev)
        val result = h.chain.evaluate(
            h.action(
                dev.deviceId, sess.sessionId, Capability.APP_INSTALL,
                category = ActionCategory.APP_LIFECYCLE, role = Role.GAST
            )
        )
        // APP_INSTALL ist am GAST-Gerät weder registriert noch erlaubt.
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.CAPABILITY, result.stoppedAt)
    }

    @Test
    fun `GAST project access is denied at PROJECT stage`() {
        val h = CoreHarness()
        val dev = h.gast()
        val sess = h.session(dev)
        val result = h.chain.evaluate(
            h.action(
                dev.deviceId, sess.sessionId, Capability.FILE_READ,
                role = Role.GAST, project = "werkstatt"
            )
        )
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.PROJECT, result.stoppedAt)
    }

    @Test
    fun `network flow without grant is denied at POLICY stage`() {
        val h = CoreHarness()
        val dev = h.admin()
        val sess = h.session(dev)
        val result = h.chain.evaluate(
            h.action(
                dev.deviceId, sess.sessionId, Capability.NETWORK,
                category = ActionCategory.NETWORK_ENABLE, networkFlow = true,
                dataFlow = DataFlow.EXTERNAL, role = Role.ADMIN
            )
        )
        // NETWORK_ENABLE ist kritisch → Gate; aber Netzwerk-Verletzung schlägt zuerst (POLICY).
        assertEquals(Decision.DENIED, result.decision)
        assertEquals(Stage.POLICY, result.stoppedAt)
    }

    @Test
    fun `network flow with explicit grant passes policy but stays critical`() {
        val h = CoreHarness()
        val dev = h.admin()
        val sess = h.session(dev, network = NetworkState.GRANTED)
        // Gerät muss ebenfalls GRANTED haben – hier nicht der Fall → dennoch DENIED.
        val result = h.chain.evaluate(
            h.action(
                dev.deviceId, sess.sessionId, Capability.NETWORK,
                category = ActionCategory.NETWORK_ENABLE, networkFlow = true,
                role = Role.ADMIN
            )
        )
        assertEquals(Decision.DENIED, result.decision)
    }

    @Test
    fun `every evaluated request leaves an audit event`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        h.chain.evaluate(h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt"))
        val auditEvents = h.audit.all
        assertTrue(auditEvents.isNotEmpty())
        assertEquals(
            de.d88.platform.model.AuditEventType.POLICY_EVALUATED,
            auditEvents.last().type
        )
    }

    @Test
    fun `chain never skips stages - full trace for approved request`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt")
        )
        val stages = result.stageTrace.map { it.stage }
        val expected = listOf(
            Stage.DEVICE, Stage.IDENTITY, Stage.TRUST, Stage.ROLE,
            Stage.PROJECT, Stage.TASK, Stage.SESSION, Stage.CAPABILITY,
            Stage.POLICY, Stage.HUMAN_GATE
        )
        assertEquals(expected, stages)
    }

    @Test
    fun `denied request trace ends at the failing stage`() {
        val h = CoreHarness()
        val dev = h.client(project = "werkstatt")
        val sess = h.session(dev, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "privat-b")
        )
        val stages = result.stageTrace.map { it.stage }
        assertEquals(Stage.PROJECT, stages.last())
        assertNull("Keine HUMAN_GATE-Stage bei früherer Ablehnung",
            stages.indexOf(Stage.HUMAN_GATE).takeIf { it >= 0 })
    }

    @Test
    fun `identity warning for policy version mismatch does not fail the chain`() {
        val h = CoreHarness()
        val dev = h.client()
        val updated = dev.copy(policyVersion = 99)
        h.registry.update(updated)
        val sess = h.session(updated, project = "werkstatt")
        val result = h.chain.evaluate(
            h.action(dev.deviceId, sess.sessionId, Capability.FILE_READ, project = "werkstatt")
        )
        // WARN wird protokolliert, Kette darf weiterlaufen.
        assertEquals(Decision.APPROVED, result.decision)
        assertTrue(
            result.stageTrace.first { it.stage == Stage.IDENTITY }.detail.contains("WARN")
        )
        assertNotNull(result)
    }
}
