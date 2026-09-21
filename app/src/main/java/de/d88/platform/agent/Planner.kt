package de.d88.platform.agent

import de.d88.platform.model.ActionCategory
import de.d88.platform.model.Capability
import de.d88.platform.model.RiskClass

/**
 * Agent Task / Plan / Step – deterministische Abstraktion der
 * Geräteübergreifenden Agentenarbeit (Spec Abschnitt 17).
 */

data class AgentTask(
    val taskId: String,
    val deviceId: String,
    val sessionId: String,
    val intent: String,
    val projectId: String? = null,
    val details: String? = null
)

data class AgentStep(
    val stepId: String,
    val description: String,
    val capability: Capability,
    val category: ActionCategory,
    val risk: RiskClass,
    /** Nur für echte ADB-Aktionen; sonst null (lokale Verarbeitung). */
    val adbCommand: String?
)

data class AgentPlan(
    val taskId: String,
    val steps: List<AgentStep>
)

/**
 * Regelbasierter, deterministischer Planner.
 *
 * Der Planner erzeugt NIE ein Kommando außerhalb der ADB-Whitelist und
 * markiert kritische Absichten (Firmware, Export, Physik) als CRITICAL –
 * die eigentliche Entscheidung trifft die Autorisierungskette.
 */
object Planner {

    fun plan(task: AgentTask): AgentPlan {
        val intent = task.intent.lowercase()
        val steps = when {
            containsAny(intent, "firmware", "update", "aktualisieren") -> listOf(
                step(
                    "Firmware-Aktualisierung vorbereiten (kritisch)",
                    Capability.ADB, ActionCategory.FIRMWARE, RiskClass.CRITICAL,
                    adbCommand = null
                )
            )
            containsAny(intent, "export", "extern", "herunterladen", "cloud") -> listOf(
                step(
                    "Daten nach außen exportieren (kritisch)",
                    Capability.NETWORK, ActionCategory.DATA_EXPORT, RiskClass.CRITICAL,
                    adbCommand = null
                )
            )
            containsAny(intent, "log", "protokoll", "crash") -> listOf(
                step(
                    "Geräte-Infos lesen",
                    Capability.DEVICE_INFO, ActionCategory.READ, RiskClass.LOW,
                    adbCommand = "devices"
                ),
                step(
                    "Letzte Log-Auszüge lesen (read-only)",
                    Capability.LOG_READ, ActionCategory.READ, RiskClass.MEDIUM,
                    adbCommand = "logcat -d -t 200"
                )
            )
            containsAny(intent, "app starten", "app stoppen", "starte die app") -> listOf(
                step(
                    "App-Lebenszyklus-Aktion (ändernd)",
                    Capability.APP_START, ActionCategory.APP_LIFECYCLE, RiskClass.MEDIUM,
                    adbCommand = "shell am start -a android.intent.action.MAIN"
                )
            )
            containsAny(intent, "analysieren", "auswerten", "analyse", "messdaten", "diagnose") -> listOf(
                step(
                    "Projekt-Daten lokal lesen",
                    Capability.FILE_READ, ActionCategory.READ, RiskClass.LOW,
                    adbCommand = null
                ),
                step(
                    "Lokale Analyse durchführen (sandboxed, ohne Netzwerk)",
                    Capability.SANDBOX, ActionCategory.ANALYZE, RiskClass.LOW,
                    adbCommand = null
                ),
                step(
                    "Wissensabfrage im lokalen RAG-Bereich",
                    Capability.RAG_QUERY, ActionCategory.ANALYZE, RiskClass.LOW,
                    adbCommand = null
                )
            )
            containsAny(intent, "bild", "kamera", "screenshot") -> listOf(
                step(
                    "Bild/Messung aufnehmen (lokale Verarbeitung)",
                    Capability.CAMERA, ActionCategory.EXECUTE, RiskClass.MEDIUM,
                    adbCommand = null
                )
            )
            else -> listOf(
                step(
                    "Geräte-Status prüfen (read-only)",
                    Capability.DEVICE_INFO, ActionCategory.READ, RiskClass.LOW,
                    adbCommand = "devices"
                )
            )
        }
        return AgentPlan(task.taskId, steps)
    }

    private fun step(
        description: String,
        capability: Capability,
        category: ActionCategory,
        risk: RiskClass,
        adbCommand: String?
    ): AgentStep = AgentStep(
        stepId = "step-${(description.hashCode().toUInt() % 0xFFFFFF).toString(16)}",
        description = description,
        capability = capability,
        category = category,
        risk = risk,
        adbCommand = adbCommand
    )

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it.lowercase()) }
}
