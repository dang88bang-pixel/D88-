package de.d88.platform.ui

import de.d88.platform.model.Capability

/**
 * Lokale Wissens-/RAG-Bereich des Prototyps.
 *
 * WICHTIG: Rein lokal. Kein Netzwerk, keine Cloud, keine Telemetrie.
 * Die "Retrieval"-Funktion ist eine simple Keyword-Scoring-Suche –
 * ein echter Embedding-Index folgt in einer späteren Milestone (Roadmap).
 */
object KnowledgeBase {

    data class Doc(
        val id: String,
        val title: String,
        val body: String
    )

    val docs: List<Doc> = listOf(
        Doc(
            "kb-roles",
            "D88-Geräterollen: ADMIN, CLIENT, GAST",
            "ADMIN ist das persönliche Hauptgerät mit voller Verwaltung, aber kritische Aktionen " +
                "bleiben Human-in-the-Loop. CLIENT nutzt D88 leistungsfähig innerhalb von Projekt, " +
                "Aufgabe, Session und Freigabe – ist ausdrücklich kein halber ADMIN. GAST erhält " +
                "einen isolierten privaten Arbeitsbereich (/guest/<device-id>/) ohne Administration, " +
                "ADB, Deployment oder Zugriff auf fremde Projekte."
        ),
        Doc(
            "kb-chain",
            "Die 13-stufige Autorisierungskette",
            "DEVICE → IDENTITY → TRUST → ROLE → PROJECT → TASK → SESSION → CAPABILITY → POLICY → " +
                "HUMAN GATE → EXECUTION → VALIDATION → AUDIT. Der Agent darf niemals eine Ebene " +
                "überspringen. Jede Stufe wird im Ergebnis-Trace dokumentiert und auditiert."
        ),
        Doc(
            "kb-trust",
            "Vertrauensstatus und Pairing",
            "Hauptpfad: DISCOVERED → PAIRING_REQUIRED → PAIRED → IDENTIFIED → TRUST_PENDING → TRUSTED. " +
                "Sonderzustände: SUSPENDED, REVOKED, EXPIRED, BLOCKED, UNKNOWN. Invarianten: " +
                "DISCOVERED ≠ TRUSTED, PAIRED ≠ AUTHORIZED, ADB_CONNECTED ≠ ADMIN."
        ),
        Doc(
            "kb-adb",
            "ADB/Debug Gateway – Transport, nicht Autorisierung",
            "ADB wird als Transport- und Debugmechanismus integriert. Der Agent erzeugt niemals " +
                "beliebige ADB-Kommandos; jedes Kommando durchläuft Capability Request → ADB Command " +
                "Policy (Whitelist) → Policy Engine → Human Gate → Debug Gateway → ADB Adapter. " +
                "Nicht auf der Whitelist ist strikt DENIED (Fail-Closed)."
        ),
        Doc(
            "kb-human",
            "Human-in-the-Loop (Human Gate)",
            "Kritische Aktionen – z. B. Firmware, Datenlöschung, Datenexport, Netzwerk aktivieren, " +
                "Capability-Installation, MCP-Verbindung, physische Aktionen, unbekannte Funktionen – " +
                "blockieren in der Freigabe-Queue, bis ein Mensch ABLEHNEN oder FREIGEBEN wählt. " +
                "Timeout-Default ist DENY."
        ),
        Doc(
            "kb-audit",
            "Causal Audit und Hash-Kette",
            "Jede relevante Geräteaktion wird kausal verknüpft (USER_REQUEST → TASK_CREATED → " +
                "PLAN_CREATED → CAPABILITY_REQUESTED → POLICY_EVALUATED → HUMAN_APPROVAL → " +
                "DEVICE_ACTION → DEVICE_RESULT → VALIDATION → CONTEXT_UPDATE). Jedes Event trägt " +
                "action_id, device_id, session_id, parent_event_id, timestamp, result, evidence und " +
                "einen SHA-256-Hash über Parent-Hash und Inhalt – Manipulation ist nachweisbar."
        ),
        Doc(
            "kb-network",
            "Netzwerk: default DENY",
            "NETWORK ist standardmäßig DENY. Eine Verbindung erfordert Capability Request → " +
                "Network Policy → Target/Protocol/Purpose → Authorization. Es gibt keine stillen " +
                "Analytics-, Telemetrie-, Crash- oder Cloud-Verbindungen. Die App deklariert " +
                "daher keine INTERNET-Permission."
        ),
        Doc(
            "kb-capabilities",
            "Device Capabilities",
            "Capabilities werden getrennt von der Rolle verwaltet (u. a. DEVICE_INFO, APP_*, LOG_READ, " +
                "SCREEN_CAPTURE, CAMERA, USB, BLE, WLAN, NETWORK, FILE_READ/WRITE, ADB, SHELL, DEBUG, " +
                "SANDBOX, REMOTE_AGENT, RAG_QUERY). Effektiv ist nur die Schnittmenge: " +
                "Gerät ∩ Session ∩ Policy."
        )
    )

    /** Simple lokale Keyword-Suche (Retrieval ohne Netzwerk). */
    fun search(query: String): List<Pair<Doc, Int>> {
        val terms = query.lowercase().split(Regex("\\W+")).filter { it.length >= 3 }
        if (terms.isEmpty()) return docs.map { it to 0 }
        return docs.map { doc ->
            val text = (doc.title + " " + doc.body).lowercase()
            val score = terms.count { text.contains(it) }
            doc to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    /** Demo: RAG_QUERY läuft über die Autorisierungskette (Capability). */
    fun ragQueryAllowed(capabilities: Set<Capability>): Boolean =
        Capability.RAG_QUERY in capabilities
}
