package de.d88.platform

import de.d88.platform.core.PolicyEngine
import de.d88.platform.model.ActionCategory
import de.d88.platform.model.ActionRequest
import de.d88.platform.model.Capability
import de.d88.platform.model.DataFlow
import de.d88.platform.model.NetworkState
import de.d88.platform.model.Policy
import de.d88.platform.model.RiskClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {

    private val policy = Policy.CURRENT
    private val engine = PolicyEngine(policy)

    private fun request(
        category: ActionCategory,
        capability: Capability = Capability.FILE_READ,
        risk: RiskClass = RiskClass.LOW,
        networkFlow: Boolean = false,
        dataFlow: DataFlow = DataFlow.LOCAL_ONLY
    ) = ActionRequest(
        actionId = "act-test",
        deviceId = "dev",
        role = de.d88.platform.model.Role.CLIENT,
        projectId = "p",
        taskId = null,
        sessionId = "s",
        capability = capability,
        target = "dev",
        requestedChange = "test",
        reason = "test",
        dataFlow = dataFlow,
        networkFlow = networkFlow,
        riskClass = risk,
        category = category,
        evidence = null
    )

    @Test
    fun `critical categories escalate to CRITICAL regardless of declared risk`() {
        for (category in listOf(
            ActionCategory.DATA_DELETE,
            ActionCategory.DATA_EXPORT,
            ActionCategory.FIRMWARE,
            ActionCategory.NETWORK_ENABLE,
            ActionCategory.CAPABILITY_INSTALL,
            ActionCategory.MCP_CONNECT,
            ActionCategory.PHYSICAL,
            ActionCategory.UNKNOWN_FUNCTION,
            ActionCategory.CONFIG_CHANGE
        )) {
            val risk = engine.escalateRisk(request(category, risk = RiskClass.LOW))
            assertEquals("$category muss CRITICAL eskalieren", RiskClass.CRITICAL, risk)
        }
    }

    @Test
    fun `network flow without grant is a hard violation`() {
        val h = CoreHarness()
        val d = h.client()
        val s = h.session(d, project = "werkstatt")
        val eval = engine.evaluate(request(networkFlow = true, capability = Capability.NETWORK), d, s)
        assertTrue(eval.violations.isNotEmpty())
        assertTrue(eval.violations.first().contains("NETWORK"))
    }

    @Test
    fun `client high risk requires human gate`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val eval = engine.evaluate(request(ActionCategory.CONFIG_CHANGE), dev, sess)
        assertTrue(eval.requiresHumanGate)
        assertEquals(RiskClass.CRITICAL, eval.finalRisk)
    }

    @Test
    fun `client low risk needs no gate`() {
        val h = CoreHarness()
        val dev = h.client()
        val sess = h.session(dev, project = "werkstatt")
        val eval = engine.evaluate(request(ActionCategory.READ), dev, sess)
        assertEquals(false, eval.requiresHumanGate)
    }

    @Test
    fun `guest critical is a hard violation`() {
        val h = CoreHarness()
        val dev = h.gast()
        val sess = h.session(dev)
        val eval = engine.evaluate(request(ActionCategory.DATA_DELETE, capability = Capability.FILE_WRITE), dev, sess)
        assertTrue(eval.violations.isNotEmpty())
    }

    @Test
    fun `sensitive capability without network flow escalates to HIGH`() {
        val risk = engine.escalateRisk(request(ActionCategory.READ, capability = Capability.ADB))
        assertEquals(RiskClass.HIGH, risk)
    }

    @Test
    fun `external data flow escalates to HIGH`() {
        val risk = engine.escalateRisk(request(ActionCategory.READ, dataFlow = DataFlow.EXTERNAL))
        assertEquals(RiskClass.HIGH, risk)
    }
}
