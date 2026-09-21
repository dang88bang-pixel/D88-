package de.d88.platform

import de.d88.platform.debug.AdbAdapter
import de.d88.platform.debug.AdbResult
import de.d88.platform.debug.DebugGateway
import de.d88.platform.debug.TransportState
import de.d88.platform.model.ActionCategory
import de.d88.platform.model.ActionRequest
import de.d88.platform.model.Capability
import de.d88.platform.model.DataFlow
import de.d88.platform.model.Role
import de.d88.platform.model.RiskClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugGatewayTest {

    private class FakeAdapter(
        private val state: TransportState,
        private var executedCommands: MutableList<String> = mutableListOf()
    ) : AdbAdapter {
        val commands = executedCommands
        override val name: String = "fake"
        override fun state(): TransportState = state
        override fun execute(deviceId: String, command: String): AdbResult {
            commands += command
            return AdbResult(deviceId, command, 0, "ok", "", 0L, realTransport = true)
        }
    }

    private fun CoreHarness.request(
        deviceId: String,
        sessionId: String,
        capability: Capability,
        category: ActionCategory,
        risk: RiskClass,
        role: Role
    ) = ActionRequest(
        actionId = "act-gw-${System.nanoTime() % 1000000}",
        deviceId = deviceId,
        role = role,
        projectId = if (role == Role.CLIENT) "werkstatt" else null,
        taskId = null,
        sessionId = sessionId,
        capability = capability,
        target = deviceId,
        requestedChange = "Testaktion",
        reason = "Test",
        dataFlow = DataFlow.LOCAL_ONLY,
        networkFlow = false,
        riskClass = risk,
        category = category,
        evidence = null
    )

    @Test
    fun `unavailable transport is reported honestly - no fake execution`() {
        val h = CoreHarness()
        val dev = h.admin()
        val sess = h.session(dev)
        val adapter = FakeAdapter(TransportState.UNAVAILABLE)
        val gateway = DebugGateway(h.chain, h.gate, adapter, h.audit, h.clock)

        val result = gateway.requestDeviceAction(
            h.request(dev.deviceId, sess.sessionId, Capability.DEVICE_INFO, ActionCategory.READ, RiskClass.LOW, Role.ADMIN),
            adbCommand = "devices"
        )
        val unavailable = result as DebugGateway.TransportUnavailable
        assertTrue(unavailable.detail.contains("NICHT ausgeführt"))
        assertTrue(adapter.commands.isEmpty(), "Kein Kommando darf ausgeführt werden")
        assertTrue(
            h.audit.all.any {
                it.type == de.d88.platform.model.AuditEventType.TRANSPORT_UNAVAILABLE
            }
        )
    }

    @Test
    fun `critical action waits for human gate and executes only after approval`() {
        val h = CoreHarness()
        val dev = h.admin()
        val sess = h.session(dev)
        val adapter = FakeAdapter(TransportState.AVAILABLE)
        val gateway = DebugGateway(h.chain, h.gate, adapter, h.audit, h.clock)

        val req = h.request(dev.deviceId, sess.sessionId, Capability.ADB, ActionCategory.CAPABILITY_INSTALL, RiskClass.HIGH, Role.ADMIN)
        val waiting = gateway.requestDeviceAction(req, adbCommand = "install app.apk")
        assertTrue(waiting is DebugGateway.WaitingForHuman)
        assertTrue(adapter.commands.isEmpty(), "Nichts vor Freigabe")

        val executed = gateway.executeAfterApproval(req.actionId, approver = "admin", adbCommand = "install app.apk")
        assertTrue(executed is DebugGateway.Executed)
        val exec = executed as DebugGateway.Executed
        assertEquals(true, exec.adbResult.realTransport)
        assertEquals(listOf("install app.apk"), adapter.commands)
    }

    @Test
    fun `denied action is never executed`() {
        val h = CoreHarness()
        val dev = h.client(trust = de.d88.platform.model.TrustState.BLOCKED)
        val sess = h.session(dev, project = "werkstatt")
        val adapter = FakeAdapter(TransportState.AVAILABLE)
        val gateway = DebugGateway(h.chain, h.gate, adapter, h.audit, h.clock)

        val result = gateway.requestDeviceAction(
            h.request(dev.deviceId, sess.sessionId, Capability.FILE_READ, ActionCategory.READ, RiskClass.LOW, Role.CLIENT),
            adbCommand = "devices"
        )
        assertTrue(result is DebugGateway.Denied)
        assertTrue(adapter.commands.isEmpty())
    }

    @Test
    fun `non-adb action executes locally without transport`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val adapter = FakeAdapter(TransportState.UNAVAILABLE)
        val gateway = DebugGateway(h.chain, h.gate, adapter, h.audit, h.clock)

        val result = gateway.requestDeviceAction(
            h.request(dev.deviceId, sess.sessionId, Capability.FILE_READ, ActionCategory.READ, RiskClass.LOW, Role.CLIENT)
        )
        assertTrue(result is DebugGateway.Executed)
        val exec = result as DebugGateway.Executed
        assertEquals(false, exec.adbResult.realTransport)
    }
}
