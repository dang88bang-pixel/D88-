package de.d88.platform.device.simulation

import de.d88.platform.model.AuthState
import de.d88.platform.model.Capability
import de.d88.platform.model.DataScope
import de.d88.platform.model.DebugScope
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.NetworkState
import de.d88.platform.model.PairingState
import de.d88.platform.model.Policy
import de.d88.platform.model.Role
import de.d88.platform.model.TrustState

/**
 * SIMULATIONS-Daten für die Prototyp-UI.
 *
 * Diese Geräte sind EXPLIZIT simuliert (simulated = true) und dienen nur dazu,
 * die D88-Oberfläche und das Autorisierungsmodell interaktiv zu testen.
 * Sie sind KEINE echten Geräte. Die UI zeigt dies als Banner an.
 */
object SimulationSeeder {

    fun seedDevices(now: Long): List<DeviceIdentity> = listOf(
        // Pixel 10 – ADMIN, TRUSTED
        DeviceIdentity(
            deviceId = "dev-pixel10",
            deviceKey = "sim-key-pixel10-0001",
            deviceType = "Smartphone",
            platform = "Android",
            platformVersion = "15",
            d88Version = "0.1.0",
            role = Role.ADMIN,
            ownerScope = "privat",
            trustState = TrustState.TRUSTED,
            pairingState = PairingState.PAIRED,
            authenticationState = AuthState.AUTHENTICATED,
            capabilities = setOf(
                Capability.DEVICE_INFO, Capability.FILE_READ, Capability.FILE_WRITE,
                Capability.APP_INSTALL, Capability.APP_REMOVE, Capability.APP_START,
                Capability.APP_STOP, Capability.LOG_READ, Capability.CAMERA,
                Capability.SCREEN_CAPTURE, Capability.ADB, Capability.SHELL,
                Capability.DEBUG, Capability.SANDBOX, Capability.NETWORK,
                Capability.RAG_QUERY
            ),
            projectScopes = setOf("werkstatt", "privat-b", "fahrzeug-a"),
            taskScopes = setOf("t-4711"),
            dataScopes = setOf(DataScope.GLOBAL, DataScope.PROJECT, DataScope.PRIVATE),
            networkState = NetworkState.DENY,
            debugScope = DebugScope.CONTROL,
            sessionScopes = emptySet(),
            policyVersion = Policy.CURRENT.version,
            simulated = true,
            createdAt = now,
            updatedAt = now
        ),
        // Tablet – CLIENT, TRUSTED, Projekt "Werkstatt"
        DeviceIdentity(
            deviceId = "dev-tablet",
            deviceKey = "sim-key-tablet-0002",
            deviceType = "Tablet",
            platform = "Android",
            platformVersion = "14",
            d88Version = "0.1.0",
            role = Role.CLIENT,
            ownerScope = "privat",
            trustState = TrustState.TRUSTED,
            pairingState = PairingState.PAIRED,
            authenticationState = AuthState.AUTHENTICATED,
            capabilities = setOf(
                Capability.DEVICE_INFO, Capability.FILE_READ, Capability.FILE_WRITE,
                Capability.CAMERA, Capability.RAG_QUERY, Capability.SANDBOX
            ),
            projectScopes = setOf("werkstatt"),
            taskScopes = setOf("t-4711"),
            dataScopes = setOf(DataScope.PROJECT, DataScope.TASK, DataScope.SESSION, DataScope.DEVICE, DataScope.PRIVATE),
            networkState = NetworkState.DENY,
            debugScope = DebugScope.NONE,
            sessionScopes = emptySet(),
            policyVersion = Policy.CURRENT.version,
            simulated = true,
            createdAt = now,
            updatedAt = now
        ),
        // Zweites Smartphone – CLIENT, eingeschränktes Vertrauen
        DeviceIdentity(
            deviceId = "dev-phone2",
            deviceKey = "sim-key-phone2-0003",
            deviceType = "Smartphone",
            platform = "Android",
            platformVersion = "15",
            d88Version = "0.1.0",
            role = Role.CLIENT,
            ownerScope = "privat",
            trustState = TrustState.TRUST_PENDING,
            pairingState = PairingState.PAIRED,
            authenticationState = AuthState.CHALLENGED,
            capabilities = setOf(
                Capability.DEVICE_INFO, Capability.FILE_READ, Capability.RAG_QUERY
            ),
            projectScopes = setOf("fahrzeug-a"),
            taskScopes = emptySet(),
            dataScopes = setOf(DataScope.PROJECT, DataScope.DEVICE),
            networkState = NetworkState.DENY,
            debugScope = DebugScope.NONE,
            sessionScopes = emptySet(),
            policyVersion = Policy.CURRENT.version,
            simulated = true,
            createdAt = now,
            updatedAt = now
        ),
        // Fremdes Gerät – GAST, isolierter Bereich
        DeviceIdentity(
            deviceId = "dev-gast",
            deviceKey = "sim-key-gast-0004",
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
            policyVersion = Policy.CURRENT.version,
            simulated = true,
            createdAt = now,
            updatedAt = now
        )
    )
}
