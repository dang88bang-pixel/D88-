package de.d88.platform.core

import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.Role

/**
 * Role Engine (Modul authorization/RoleEngine, Spec Abschnitt 19).
 *
 * Die Rolle definiert nur die Grundlage – niemals die vollständige Berechtigung.
 */
object RoleEngine {

    /**
     * Basisrechte der Rolle (Spezifikationsübersicht).
     * Diese Liste beschreibt, was die Rolle grundsätzlich darf – jede konkrete
     * Aktion muss zusätzlich Trust, Capability, Scope, Session und Policy bestehen.
     */
    fun baseline(role: Role): List<String> = when (role) {
        Role.ADMIN -> listOf(
            "vollständige D88-Verwaltung",
            "Verwaltung verbundener Geräte",
            "Verwaltung von Projekten, Sessions, Capabilities, Workflows",
            "Sandbox, App-Erstellung/Deployment, Debug-/ADB-Verwaltung",
            "Knowledge/RAG, Context Graph, Agent-Konfiguration",
            "Netzwerk- und Sicherheits-/Policy-Konfiguration",
            "Diagnose und Recovery"
        )
        Role.CLIENT -> listOf(
            "freigegebene Projekte öffnen",
            "eigene bzw. freigegebene Daten analysieren",
            "RAG/Knowledge nutzen",
            "Agent Sessions verwenden",
            "freigegebene Workflows ausführen",
            "Ergebnisse anzeigen",
            "Aufgaben an den D88-Server senden"
        )
        Role.GAST -> listOf(
            "eigener D88-Speicherbereich (GUEST)",
            "freigegebene Dateien",
            "definierte lokale Datenverarbeitung"
        )
    }

    /**
     * Grundlegende Rollen-Kompatibilität: Welche Projekt-/Aufgabenbindung verlangt die Rolle?
     *  - ADMIN: Projektbindung optional (Verwaltungsebene)
     *  - CLIENT: Projektbindung PFLICHT (keine projektlosen Vollzugriffe)
     *  - GAST: keine Projekt-/Aufgabenbindung möglich
     */
    fun requiresProject(role: Role): Boolean = role == Role.CLIENT

    fun allowsProjectBinding(role: Role): Boolean = role != Role.GAST

    fun roleFor(device: DeviceIdentity): Role = device.role
}
