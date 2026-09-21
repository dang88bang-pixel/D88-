package de.d88.platform.data

import android.content.Context
import de.d88.platform.model.AuditEvent
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.AuthState
import de.d88.platform.model.Capability
import de.d88.platform.model.DataScope
import de.d88.platform.model.DebugScope
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.NetworkState
import de.d88.platform.model.PairingState
import de.d88.platform.model.Role
import de.d88.platform.model.TrustState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lokale JSON-Persistenz (lokal-first, keine Cloud).
 * Speichert Geräte-Registry und Audit-Trail in filesDir der App.
 */
class JsonStorage(context: Context) {

    private val devicesFile = File(context.filesDir, "d88-devices.json")
    private val auditFile = File(context.filesDir, "d88-audit.json")

    // ------------------------------------------------------------------ Geräte

    fun saveDevices(devices: List<DeviceIdentity>) {
        val arr = JSONArray()
        for (d in devices) {
            arr.put(JSONObject().apply {
                put("deviceId", d.deviceId)
                put("deviceKey", d.deviceKey)
                put("deviceType", d.deviceType)
                put("platform", d.platform)
                put("platformVersion", d.platformVersion)
                put("d88Version", d.d88Version)
                put("role", d.role.name)
                put("ownerScope", d.ownerScope)
                put("trustState", d.trustState.name)
                put("pairingState", d.pairingState.name)
                put("authenticationState", d.authenticationState.name)
                put("capabilities", JSONArray(d.capabilities.map { it.name }))
                put("projectScopes", JSONArray(d.projectScopes.toList()))
                put("taskScopes", JSONArray(d.taskScopes.toList()))
                put("dataScopes", JSONArray(d.dataScopes.map { it.name }))
                put("networkState", d.networkState.name)
                put("debugScope", d.debugScope.name)
                put("sessionScopes", JSONArray(d.sessionScopes.toList()))
                put("policyVersion", d.policyVersion)
                put("simulated", d.simulated)
                put("createdAt", d.createdAt)
                put("updatedAt", d.updatedAt)
            })
        }
        devicesFile.writeText(arr.toString(2))
    }

    fun loadDevices(): List<DeviceIdentity> {
        if (!devicesFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(devicesFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeviceIdentity(
                    deviceId = o.getString("deviceId"),
                    deviceKey = o.getString("deviceKey"),
                    deviceType = o.optString("deviceType", "unbekannt"),
                    platform = o.optString("platform", "Android"),
                    platformVersion = o.optString("platformVersion", "?"),
                    d88Version = o.optString("d88Version", "?"),
                    role = Role.valueOf(o.getString("role")),
                    ownerScope = o.optString("ownerScope", "privat"),
                    trustState = TrustState.valueOf(o.getString("trustState")),
                    pairingState = PairingState.valueOf(o.optString("pairingState", "NOT_PAIRED")),
                    authenticationState = AuthState.valueOf(o.optString("authenticationState", "UNAUTHENTICATED")),
                    capabilities = o.getJSONArray("capabilities").let { ja ->
                        (0 until ja.length()).mapNotNull { Capability.valueOf(ja.getString(it)) }.toSet()
                    },
                    projectScopes = o.getJSONArray("projectScopes").let { ja ->
                        (0 until ja.length()).map { ja.getString(it) }.toSet()
                    },
                    taskScopes = o.getJSONArray("taskScopes").let { ja ->
                        (0 until ja.length()).map { ja.getString(it) }.toSet()
                    },
                    dataScopes = o.getJSONArray("dataScopes").let { ja ->
                        (0 until ja.length()).mapNotNull { DataScope.valueOf(ja.getString(it)) }.toSet()
                    },
                    networkState = NetworkState.valueOf(o.optString("networkState", "DENY")),
                    debugScope = DebugScope.valueOf(o.optString("debugScope", "NONE")),
                    sessionScopes = o.getJSONArray("sessionScopes").let { ja ->
                        (0 until ja.length()).map { ja.getString(it) }.toSet()
                    },
                    policyVersion = o.optInt("policyVersion", 1),
                    simulated = o.optBoolean("simulated", true),
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ Audit

    fun saveAudit(events: List<AuditEvent>) {
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject().apply {
                put("eventId", e.eventId)
                put("type", e.type.name)
                put("actionId", e.actionId ?: JSONObject.NULL)
                put("deviceId", e.deviceId ?: JSONObject.NULL)
                put("sessionId", e.sessionId ?: JSONObject.NULL)
                put("projectId", e.projectId ?: JSONObject.NULL)
                put("taskId", e.taskId ?: JSONObject.NULL)
                put("capability", e.capability?.name ?: JSONObject.NULL)
                put("policyDecision", e.policyDecision ?: JSONObject.NULL)
                put("authorization", e.authorization ?: JSONObject.NULL)
                put("parentEventId", e.parentEventId ?: JSONObject.NULL)
                put("timestamp", e.timestamp)
                put("result", e.result ?: JSONObject.NULL)
                put("evidence", e.evidence ?: JSONObject.NULL)
                put("hash", e.hash)
                put("parentHash", e.parentHash ?: JSONObject.NULL)
            })
        }
        auditFile.writeText(arr.toString(2))
    }

    fun loadAudit(): List<AuditEvent> {
        if (!auditFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(auditFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AuditEvent(
                    eventId = o.getString("eventId"),
                    type = AuditEventType.valueOf(o.getString("type")),
                    actionId = if (o.isNull("actionId")) null else o.getString("actionId"),
                    deviceId = if (o.isNull("deviceId")) null else o.getString("deviceId"),
                    sessionId = if (o.isNull("sessionId")) null else o.getString("sessionId"),
                    projectId = if (o.isNull("projectId")) null else o.getString("projectId"),
                    taskId = if (o.isNull("taskId")) null else o.getString("taskId"),
                    capability = if (o.isNull("capability")) null else Capability.valueOf(o.getString("capability")),
                    policyDecision = if (o.isNull("policyDecision")) null else o.getString("policyDecision"),
                    authorization = if (o.isNull("authorization")) null else o.getString("authorization"),
                    parentEventId = if (o.isNull("parentEventId")) null else o.getString("parentEventId"),
                    timestamp = o.getLong("timestamp"),
                    result = if (o.isNull("result")) null else o.getString("result"),
                    evidence = if (o.isNull("evidence")) null else o.getString("evidence"),
                    hash = o.getString("hash"),
                    parentHash = if (o.isNull("parentHash")) null else o.getString("parentHash")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
