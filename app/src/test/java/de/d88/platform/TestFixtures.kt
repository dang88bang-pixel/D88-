package de.d88.platform

import de.d88.platform.core.AuthorizationChain
import de.d88.platform.core.AuditTrail
import de.d88.platform.core.HumanGate
import de.d88.platform.core.PolicyEngine
import de.d88.platform.device.DeviceRegistry
import de.d88.platform.model.AgentSession
import de.d88.platform.model.AuthState
import de.d88.platform.model.Capability
import de.d88.platform.model.DataScope
import de.d88.platform.model.DebugScope
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.HumanGateState
import de.d88.platform.model.NetworkState
import de.d88.platform.model.PairingState
import de.d88.platform.model.Policy
import de.d88.platform.model.Role
import de.d88.platform.model.SessionAuthState
import de.d88.platform.model.TrustState

/** Testuhr – deterministische Zeit für TTL/Timeout-Tests. */
class MutableClock(var t: Long = 1_000_000L) : de.d88.platform.model.Clock {
    override fun now(): Long = t
}

/** Test-Assembly der Kern-Module (ohne Android). */
class CoreHarness {
    val clock = MutableClock()
    val audit = AuditTrail(clock)
    val registry = DeviceRegistry()
    val policy = Policy.CURRENT
    val gate = HumanGate(audit, clock)
    val chain = AuthorizationChain(registry, policy, PolicyEngine(policy), gate, audit, clock)

    fun admin(deviceId: String = "dev-admin", trust: TrustState = TrustState.TRUSTED): DeviceIdentity {
        val d = DeviceIdentity(
            deviceId = deviceId,
            deviceKey = "key-$deviceId",
            deviceType = "Smartphone",
            platform = "Android",
            platformVersion = "15",
            d88Version = "0.1.0",
            role = Role.ADMIN,
            ownerScope = "privat",
            trustState = trust,
            pairingState = PairingState.PAIRED,
            authenticationState = AuthState.AUTHENTICATED,
            capabilities = setOf(
                Capability.DEVICE_INFO, Capability.FILE_READ, Capability.FILE_WRITE,
                Capability.ADB, Capability.SHELL, Capability.DEBUG, Capability.SANDBOX,
                Capability.NETWORK, Capability.RAG_QUERY
            ),
            projectScopes = setOf("werkstatt", "privat-b"),
            taskScopes = setOf("t-1"),
            dataScopes = setOf(DataScope.GLOBAL),
            networkState = NetworkState.DENY,
            debugScope = DebugScope.CONTROL,
            sessionScopes = emptySet(),
            policyVersion = policy.version,
            simulated = true,
            createdAt = clock.now(),
            updatedAt = clock.now()
        )
        registry.register(d)
        return d
    }

    fun client(
        deviceId: String = "dev-client",
        project: String = "werkstatt",
        trust: TrustState = TrustState.TRUSTED
    ): DeviceIdentity {
        val d = DeviceIdentity(
            deviceId = deviceId,
            deviceKey = "key-$deviceId",
            deviceType = "Tablet",
            platform = "Android",
            platformVersion = "14",
            d88Version = "0.1.0",
            role = Role.CLIENT,
            ownerScope = "privat",
            trustState = trust,
            pairingState = PairingState.PAIRED,
            authenticationState = AuthState.AUTHENTICATED,
            capabilities = setOf(
                Capability.DEVICE_INFO, Capability.FILE_READ, Capability.FILE_WRITE,
                Capability.SANDBOX, Capability.CAMERA, Capability.RAG_QUERY, Capability.ADB
            ),
            projectScopes = setOf(project),
            taskScopes = setOf("t-4711"),
            dataScopes = setOf(DataScope.PROJECT),
            networkState = NetworkState.DENY,
            debugScope = DebugScope.NONE,
            sessionScopes = emptySet(),
            policyVersion = policy.version,
            simulated = true,
            createdAt = clock.now(),
            updatedAt = clock.now()
        )
        registry.register(d)
        return d
    }

    fun gast(deviceId: String = "dev-gast"): DeviceIdentity {
        val d = DeviceIdentity(
            deviceId = deviceId,
            deviceKey = "key-$deviceId",
            deviceType = "Smartphone",
            platform = "Android",
            platformVersion = "14",
            d88Version = "0.1.0",
            role = Role.GAST,
            ownerScope = "gast",
            trustState = TrustState.TRUST_PENDING,
            pairingState = PairingState.PAIRED,
            authenticationState = AuthState.AUTHENTICATED,
            capabilities = setOf(
                Capability.DEVICE_INFO, Capability.FILE_READ, Capability.FILE_WRITE,
                Capability.RAG_QUERY
            ),
            projectScopes = emptySet(),
            taskScopes = emptySet(),
            dataScopes = setOf(DataScope.GUEST),
            networkState = NetworkState.DENY,
            debugScope = DebugScope.NONE,
            sessionScopes = emptySet(),
            policyVersion = policy.version,
            simulated = true,
            createdAt = clock.now(),
            updatedAt = clock.now()
        )
        registry.register(d)
        return d
    }

    fun session(
        device: DeviceIdentity,
        project: String? = null,
        ttlMs: Long = 3_600_000L,
        network: NetworkState = NetworkState.DENY
    ): AgentSession {
        val s = AgentSession(
            sessionId = "sess-test-${device.deviceId}",
            deviceId = device.deviceId,
            role = device.role,
            projectId = project,
            taskId = null,
            capabilities = device.capabilities,
            dataScope = if (device.role == Role.GAST) DataScope.GUEST else DataScope.PROJECT,
            networkState = network,
            deviceScope = device.deviceId,
            policyVersion = policy.version,
            authorizationState = SessionAuthState.ACTIVE,
            humanGateState = HumanGateState.NOT_REQUIRED,
            startedAt = clock.now(),
            endsAt = clock.now() + ttlMs
        )
        registry.registerSession(s)
        return s
    }
}
