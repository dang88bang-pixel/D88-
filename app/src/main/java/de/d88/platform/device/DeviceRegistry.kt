package de.d88.platform.device

import de.d88.platform.model.AgentSession
import de.d88.platform.model.DeviceIdentity

/**
 * Device Registry (Modul device/DeviceRegistry, Spec Abschnitt 19).
 *
 * Zentrale register aller D88-Geräte und aktiven Agent-Sessions.
 * Verbindung eines Geräts verleiht NICHT automatisch Rechte – die Registry
 * hält nur Identität und Zustände; Autorisierung läuft ausschließlich
 * über die AuthorizationChain.
 */
class DeviceRegistry {

    private val devices = LinkedHashMap<String, DeviceIdentity>()
    private val sessions = LinkedHashMap<String, AgentSession>()

    // ------------------------------------------------------------------ Geräte

    @Synchronized
    fun register(device: DeviceIdentity): DeviceIdentity {
        devices[device.deviceId] = device
        return device
    }

    @Synchronized
    fun find(deviceId: String): DeviceIdentity? = devices[deviceId]

    @Synchronized
    fun all(): List<DeviceIdentity> = devices.values.toList()

    @Synchronized
    fun update(device: DeviceIdentity): DeviceIdentity {
        devices[device.deviceId] = device
        return device
    }

    @Synchronized
    fun remove(deviceId: String): DeviceIdentity? = devices.remove(deviceId)

    // ------------------------------------------------------------------ Sessions

    @Synchronized
    fun registerSession(session: AgentSession): AgentSession {
        sessions[session.sessionId] = session
        return session
    }

    @Synchronized
    fun findSession(sessionId: String?): AgentSession? =
        sessionId?.let { sessions[it] }

    @Synchronized
    fun allSessions(): List<AgentSession> = sessions.values.toList()

    @Synchronized
    fun sessionsForDevice(deviceId: String): List<AgentSession> =
        sessions.values.filter { it.deviceId == deviceId }

    @Synchronized
    fun endSession(sessionId: String): AgentSession? = sessions.remove(sessionId)
}
