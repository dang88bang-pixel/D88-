package de.d88.platform

import de.d88.platform.agent.AgentSessionManager
import de.d88.platform.model.Capability
import de.d88.platform.model.DataScope
import de.d88.platform.model.NetworkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionManagerTest {

    private fun harness() = CoreHarness()

    @Test
    fun `client session requires project binding`() {
        val h = harness()
        val dev = h.client()
        val manager = AgentSessionManager(h.registry, h.audit, h.clock)
        val result = manager.createSession(
            deviceId = dev.deviceId, projectId = null, taskId = null,
            capabilities = setOf(Capability.FILE_READ), dataScope = DataScope.PROJECT
        )
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message!!.contains("Projektbindung"))
    }

    @Test
    fun `client session in scoped project is created with grant`() {
        val h = harness()
        val dev = h.client()
        val manager = AgentSessionManager(h.registry, h.audit, h.clock)
        val result = manager.createSession(
            deviceId = dev.deviceId, projectId = "werkstatt", taskId = null,
            capabilities = setOf(Capability.FILE_READ, Capability.RAG_QUERY),
            dataScope = DataScope.PROJECT, ttlMs = 60_000
        )
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("werkstatt", session.projectId)
        assertTrue(session.isGranted(h.clock.now()))
        h.clock.t += 120_000
        assertFalse(session.isGranted(h.clock.now()))
    }

    @Test
    fun `gast session is restricted to guest scope without projects`() {
        val h = harness()
        val dev = h.gast()
        val manager = AgentSessionManager(h.registry, h.audit, h.clock)
        val result = manager.createSession(
            deviceId = dev.deviceId, projectId = "werkstatt", taskId = null,
            capabilities = setOf(Capability.FILE_READ), dataScope = DataScope.GUEST
        )
        assertFalse(result.isSuccess)
        val ok = manager.createSession(
            deviceId = dev.deviceId, projectId = null, taskId = null,
            capabilities = setOf(Capability.FILE_READ), dataScope = DataScope.GUEST
        )
        assertTrue(ok.isSuccess)
        assertEquals(DataScope.GUEST, ok.getOrThrow().dataScope)
    }

    @Test
    fun `session capabilities cannot exceed device capabilities`() {
        val h = harness()
        val dev = h.gast()
        val manager = AgentSessionManager(h.registry, h.audit, h.clock)
        val result = manager.createSession(
            deviceId = dev.deviceId, projectId = null, taskId = null,
            capabilities = setOf(Capability.ADB), dataScope = DataScope.GUEST
        )
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message!!.contains("fehlen"))
    }

    @Test
    fun `ending a session invalidates the grant`() {
        val h = harness()
        val dev = h.client()
        val manager = AgentSessionManager(h.registry, h.audit, h.clock)
        val session = manager.createSession(
            deviceId = dev.deviceId, projectId = "werkstatt", taskId = null,
            capabilities = setOf(Capability.FILE_READ), dataScope = DataScope.PROJECT
        ).getOrThrow()
        assertTrue(h.registry.findSession(session.sessionId)!!.isGranted(h.clock.now()))
        manager.endSession(session.sessionId)
        assertEquals(null, h.registry.findSession(session.sessionId))
    }

    @Test
    fun `default network state of a session is DENY`() {
        val h = harness()
        val dev = h.admin()
        val manager = AgentSessionManager(h.registry, h.audit, h.clock)
        val session = manager.createSession(
            deviceId = dev.deviceId, projectId = null, taskId = null,
            capabilities = setOf(Capability.DEVICE_INFO), dataScope = DataScope.GLOBAL
        ).getOrThrow()
        assertEquals(NetworkState.DENY, session.networkState)
    }
}
