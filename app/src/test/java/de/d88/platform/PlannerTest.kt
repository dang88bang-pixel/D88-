package de.d88.platform

import de.d88.platform.agent.AgentTask
import de.d88.platform.agent.Planner
import de.d88.platform.debug.AdbCommandPolicy
import de.d88.platform.model.ActionCategory
import de.d88.platform.model.Capability
import de.d88.platform.model.RiskClass
import de.d88.platform.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {

    private fun plan(intent: String) = Planner.plan(
        AgentTask(
            taskId = "task-test",
            deviceId = "dev",
            sessionId = "sess",
            intent = intent
        )
    )

    @Test
    fun `firmware intent is critical and single-step`() {
        val p = plan("Bitte Firmware aktualisieren")
        assertEquals(1, p.steps.size)
        val step = p.steps.first()
        assertEquals(ActionCategory.FIRMWARE, step.category)
        assertEquals(RiskClass.CRITICAL, step.risk)
        assertTrue(step.description.contains("kritisch", ignoreCase = true))
    }

    @Test
    fun `export intent is critical`() {
        val p = plan("Daten extern exportieren")
        assertEquals(ActionCategory.DATA_EXPORT, p.steps.first().category)
        assertEquals(RiskClass.CRITICAL, p.steps.first().risk)
    }

    @Test
    fun `analysis intent plans local steps`() {
        val p = plan("Messdaten in Projekt Werkstatt analysieren")
        assertEquals(3, p.steps.size)
        val capabilities = p.steps.map { it.capability }
        assertTrue(Capability.FILE_READ in capabilities)
        assertTrue(Capability.SANDBOX in capabilities)
        assertTrue(Capability.RAG_QUERY in capabilities)
        p.steps.forEach {
            assertEquals(null, it.adbCommand, "Analyse-Schritte sind lokal: ${it.description}")
        }
    }

    @Test
    fun `log intent uses read-only adb commands`() {
        val p = plan("Crash-Logs prüfen")
        assertEquals(2, p.steps.size)
        p.steps.forEach { step ->
            assertNotNull(step.adbCommand)
            val check = AdbCommandPolicy.evaluate(Role.ADMIN, step.capability, step.adbCommand!!)
            assertEquals(
                "Planner darf nur Whitelist-Kommandos erzeugen: ${step.adbCommand}",
                AdbCommandPolicy.Verdict.ALLOWED, check.verdict
            )
        }
    }

    @Test
    fun `default intent is a read-only device probe`() {
        val p = plan("irgendwas völlig anderes")
        assertEquals(1, p.steps.size)
        val step = p.steps.first()
        assertEquals(ActionCategory.READ, step.category)
        assertEquals(RiskClass.LOW, step.risk)
        assertEquals("devices", step.adbCommand)
    }

    @Test
    fun `planner never emits gate-denied adb commands`() {
        val intents = listOf(
            "Firmware aktualisieren",
            "Logs prüfen",
            "App starten",
            "Messdaten analysieren",
            "Screenshot machen",
            "Daten extern exportieren"
        )
        for (intent in intents) {
            val p = plan(intent)
            for (step in p.steps) {
                if (step.adbCommand != null) {
                    val check = AdbCommandPolicy.evaluate(Role.ADMIN, Capability.ADB, step.adbCommand)
                    assertEquals(
                        "Intent '$intent': Kommando '${step.adbCommand}' wäre DENIED",
                        false,
                        check.verdict == AdbCommandPolicy.Verdict.DENIED
                    )
                }
            }
        }
    }
}
