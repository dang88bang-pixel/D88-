package de.d88.platform.core

import de.d88.platform.model.DataFlow
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.Role

/**
 * Scope Engine (Modul authorization/ScopeEngine, Spec Abschnitt 19).
 *
 * Geräteseitige Prüfung der Projekt-/Aufgaben-/Datenfluss-Bindung
 * (Spec Abschnitt 9 + 11). Session-Übereinstimmung ist Zuständigkeit
 * der SESSION-Stage der Kette.
 */
object ScopeEngine {

    /**
     * Projekt- und Aufgabenbindung (Geräteseite) prüfen.
     * Rückgabe: null = OK, sonst Begründung der Verletzung.
     */
    fun checkProjectAndTask(
        device: DeviceIdentity,
        projectId: String?,
        taskId: String?
    ): String? {
        when (device.role) {
            Role.GAST -> {
                if (projectId != null) {
                    return "GAST hat keinen Zugriff auf Projekte (Projekt: $projectId)"
                }
                if (taskId != null) {
                    return "GAST hat keinen Zugriff auf Aufgaben (Task: $taskId)"
                }
            }
            Role.CLIENT -> {
                if (projectId == null) {
                    return "CLIENT benötigt eine Projektbindung"
                }
                if (projectId !in device.projectScopes) {
                    return "Projekt '$projectId' nicht im Project-Scope des Geräts " +
                        "(erlaubt: ${device.projectScopes.joinToString()})"
                }
                if (taskId != null && device.taskScopes.isNotEmpty() && taskId !in device.taskScopes) {
                    return "Task '$taskId' nicht im Task-Scope des Geräts"
                }
            }
            Role.ADMIN -> {
                if (projectId != null && device.projectScopes.isNotEmpty() &&
                    projectId !in device.projectScopes
                ) {
                    return "Projekt '$projectId' nicht im Project-Scope des Admin-Geräts"
                }
                if (taskId != null && device.taskScopes.isNotEmpty() && taskId !in device.taskScopes) {
                    return "Task '$taskId' nicht im Task-Scope des Admin-Geräts"
                }
            }
        }
        return null
    }

    /**
     * Datenfluss-Scope prüfen (Spec Abschnitt 11: strikte Trennung).
     * Rückgabe: null = OK, sonst Begründung der Verletzung.
     */
    fun checkDataFlow(device: DeviceIdentity, dataFlow: DataFlow): String? {
        when (device.role) {
            Role.GAST -> {
                if (dataFlow != DataFlow.LOCAL_ONLY) {
                    return "GAST darf Daten nur lokal verarbeiten (DataFlow $dataFlow verweigert)"
                }
            }
            Role.CLIENT -> {
                if (dataFlow == DataFlow.EXTERNAL) {
                    return "CLIENT: externer Datenfluss erfordert separate Freigabe (Policy)"
                }
            }
            Role.ADMIN -> Unit
        }
        return null
    }
}
