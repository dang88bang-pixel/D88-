package de.d88.platform

import de.d88.platform.core.ScopeEngine
import de.d88.platform.model.DataFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeEngineTest {

    @Test
    fun `client needs project binding`() {
        val h = CoreHarness()
        val dev = h.client()
        val violation = ScopeEngine.checkProjectAndTask(dev, null, null)
        assertNotNull(violation)
        assertTrue(violation!!.contains("Projektbindung"))
    }

    @Test
    fun `client project must be in scope`() {
        val h = CoreHarness()
        val dev = h.client(project = "werkstatt")
        assertNull(ScopeEngine.checkProjectAndTask(dev, "werkstatt", null))
        assertTrue(ScopeEngine.checkProjectAndTask(dev, "privat-b", null)!!.contains("Project-Scope"))
    }

    @Test
    fun `gast gets no project or task access`() {
        val h = CoreHarness()
        val dev = h.gast()
        assertNull(ScopeEngine.checkProjectAndTask(dev, null, null))
        assertTrue(ScopeEngine.checkProjectAndTask(dev, "werkstatt", null)!!.contains("GAST"))
        assertTrue(ScopeEngine.checkProjectAndTask(dev, null, "t-1")!!.contains("GAST"))
    }

    @Test
    fun `admin without project binding is fine`() {
        val h = CoreHarness()
        val dev = h.admin()
        assertNull(ScopeEngine.checkProjectAndTask(dev, null, null))
        assertNull(ScopeEngine.checkProjectAndTask(dev, "werkstatt", null))
        assertTrue(ScopeEngine.checkProjectAndTask(dev, "fremd", null)!!.contains("Project-Scope"))
    }

    @Test
    fun `gast data flow must be local only`() {
        val h = CoreHarness()
        val dev = h.gast()
        assertNull(ScopeEngine.checkDataFlow(dev, DataFlow.LOCAL_ONLY))
        assertTrue(ScopeEngine.checkDataFlow(dev, DataFlow.EXTERNAL)!!.contains("lokal"))
    }

    @Test
    fun `client external data flow needs separate grant`() {
        val h = CoreHarness()
        val dev = h.client()
        assertNull(ScopeEngine.checkDataFlow(dev, DataFlow.DEVICE_TO_SERVER))
        assertTrue(ScopeEngine.checkDataFlow(dev, DataFlow.EXTERNAL)!!.contains("separate Freigabe"))
    }

    @Test
    fun `admin data flows are not scope-restricted here`() {
        val h = CoreHarness()
        val dev = h.admin()
        assertNull(ScopeEngine.checkDataFlow(dev, DataFlow.EXTERNAL))
        assertEquals(null, ScopeEngine.checkDataFlow(dev, DataFlow.LOCAL_ONLY))
    }
}
