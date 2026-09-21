package de.d88.platform.core

import de.d88.platform.agent.AgentRuntime
import de.d88.platform.agent.AgentSessionManager
import de.d88.platform.debug.DebugGateway
import de.d88.platform.debug.LocalAdbAdapter
import de.d88.platform.device.DeviceManager
import de.d88.platform.device.DeviceRegistry
import de.d88.platform.device.simulation.SimulationSeeder
import de.d88.platform.model.AgentSession
import de.d88.platform.model.Clock
import de.d88.platform.model.DataScope
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.Policy
import de.d88.platform.model.Role
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * D88-Core: die zentrale Service-Graph (Konstruktor-Injection, kein Framework).
 *
 * Zusammenschaltung gemäß Spec:
 *
 *   DeviceRegistry ←→ DeviceManager
 *        ↓
 *   AuthorizationChain (PolicyEngine, Trust, Scope, Capability)
 *        ↓
 *   HumanGate (zentr. Human-in-the-Loop)
 *        ↓
 *   DebugGateway → AdbAdapter (Transport)
 *        ↓
 *   AgentRuntime (Query-Workers) → AuditTrail (Causal Audit)
 */
class D88Core(val clock: Clock = Clock.SYSTEM) {

    val policy: Policy = Policy.CURRENT
    val audit = AuditTrail(clock)
    val registry = DeviceRegistry()
    val gate = HumanGate(audit, clock)
    val policyEngine = PolicyEngine(policy)
    val chain = AuthorizationChain(registry, policy, policyEngine, gate, audit, clock)
    val adbAdapter = LocalAdbAdapter()
    val gateway = DebugGateway(chain, gate, adbAdapter, audit, clock)
    val deviceManager = DeviceManager(registry, audit, clock)
    val sessionManager = AgentSessionManager(registry, audit, clock)

    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "d88-query-worker").apply { isDaemon = true }
    }

    val runtime = AgentRuntime(chain, gate, audit, gateway, executor, clock).apply {
        setRoleResolver { deviceId ->
            registry.find(deviceId)?.role
                ?: throw IllegalStateException("Gerät '$deviceId' unbekannt – Fail-Closed")
        }
    }

    val isSimulation: Boolean
        get() = registry.all().any { it.simulated }

    /**
     * Legt (im Prototyp) SIMULIERTE Geräte an, damit die Oberfläche und das
     * Autorisierungsmodell interaktiv prüfbar sind. Klar als Simulation gekennzeichnet.
     */
    fun seedSimulationIfEmpty(): Boolean {
        if (registry.all().isNotEmpty()) return false
        for (device in SimulationSeeder.seedDevices(clock.now())) {
            registry.register(device)
            audit.record(
                de.d88.platform.model.AuditEventType.DEVICE_REGISTERED,
                deviceId = device.deviceId,
                result = "SIMULATION – $device.role / ${device.trustState}"
            )
        }
        return true
    }

    /**
     * Stellt sicher, dass für ein Gerät eine gültige Session existiert;
     * legt andernfalls eine neue an (Grant = Geräte-Capabilities, TTL begrenzt).
     */
    fun ensureSession(device: DeviceIdentity, projectId: String?, ttlMs: Long = 30 * 60 * 1000L): AgentSession {
        val active = registry.sessionsForDevice(device.deviceId)
            .firstOrNull { it.isGranted(clock.now()) }
        if (active != null) return active

        val dataScope = when (device.role) {
            Role.GAST -> DataScope.GUEST
            Role.CLIENT -> DataScope.PROJECT
            Role.ADMIN -> DataScope.GLOBAL
        }
        val project = when (device.role) {
            Role.CLIENT -> projectId ?: device.projectScopes.first()
            else -> projectId
        }
        val session = sessionManager.createSession(
            deviceId = device.deviceId,
            projectId = project,
            taskId = null,
            capabilities = device.capabilities,
            dataScope = dataScope
        ).getOrThrow()
        return session
    }
}
