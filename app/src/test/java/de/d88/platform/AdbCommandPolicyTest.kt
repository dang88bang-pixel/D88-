package de.d88.platform

import de.d88.platform.debug.AdbCommandPolicy
import de.d88.platform.model.Capability
import de.d88.platform.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbCommandPolicyTest {

    private fun check(command: String, role: Role = Role.ADMIN, capability: Capability? = Capability.ADB) =
        AdbCommandPolicy.evaluate(role, capability, command)

    @Test
    fun `read-only discovery commands are allowed`() {
        assertEquals(AdbCommandPolicy.Verdict.ALLOWED, check("devices").verdict)
        assertEquals(AdbCommandPolicy.Verdict.ALLOWED, check("devices -l").verdict)
        assertEquals(AdbCommandPolicy.Verdict.ALLOWED, check("version").verdict)
        assertEquals(AdbCommandPolicy.Verdict.ALLOWED, check("wait-for-device").verdict)
    }

    @Test
    fun `read-only shell probes are allowed`() {
        assertEquals(AdbCommandPolicy.Verdict.ALLOWED, check("shell getprop ro.product.model").verdict)
        assertEquals(AdbCommandPolicy.Verdict.ALLOWED, check("logcat -d -t 200").verdict)
    }

    @Test
    fun `mutating commands require human gate`() {
        assertEquals(AdbCommandPolicy.Verdict.REQUIRES_GATE, check("install app.apk").verdict)
        assertEquals(AdbCommandPolicy.Verdict.REQUIRES_GATE, check("uninstall com.example").verdict)
        assertEquals(AdbCommandPolicy.Verdict.REQUIRES_GATE, check("shell am start -a android.intent.action.MAIN").verdict)
        assertEquals(AdbCommandPolicy.Verdict.REQUIRES_GATE, check("tcpip 5555").verdict)
    }

    @Test
    fun `unknown commands are denied (fail closed)`() {
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("shell rm -rf /").verdict)
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("shell su").verdict)
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("restore backup.ab").verdict)
    }

    @Test
    fun `missing capability is denied`() {
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("devices", capability = null).verdict)
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("devices", capability = Capability.FILE_READ).verdict)
    }

    @Test
    fun `gast never has adb rights`() {
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("devices", role = Role.GAST).verdict)
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("install app.apk", role = Role.GAST).verdict)
    }

    @Test
    fun `shell injection attempts are denied`() {
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("devices; rm -rf /").verdict)
        assertEquals(AdbCommandPolicy.Verdict.DENIED, check("version && curl evil").verdict)
    }
}
