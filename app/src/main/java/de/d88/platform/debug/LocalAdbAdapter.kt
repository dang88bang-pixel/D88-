package de.d88.platform.debug

/**
 * Lokaler ADB-Adapter: prüft, ob ein `adb`-Binary in der Umgebung existiert
 * und nutzt es, falls vorhanden (z. B. auf einem gehärteten Host-Gerät).
 *
 * Ehrliches Verhalten:
 *  - Kein Binary  → state() = UNAVAILABLE, execute() wird ABGELEHNT (kein Fake).
 *  - Binary da    → echtes Kommando wird ausgeführt, exit code wird gemeldet.
 *
 * Hinweis: In einer normalen Android-App-Sandbox ist adb nicht vorhanden –
 * in der App zeigt D88 dann "Transport nicht verfügbar" (kein Fake-Erfolg).
 */
class LocalAdbAdapter(private val adbBinary: String = "adb") : AdbAdapter {

    override val name: String = "local-adb"

    private val available: Boolean by lazy { isBinaryAvailable() }

    override fun state(): TransportState =
        if (available) TransportState.AVAILABLE else TransportState.UNAVAILABLE

    override fun execute(deviceId: String, command: String): AdbResult {
        if (!available) {
            return AdbResult(
                deviceId = deviceId,
                command = command,
                exitCode = null,
                stdout = "",
                stderr = "Transport nicht verfügbar: adb-Binary '$adbBinary' nicht gefunden",
                executedAt = System.currentTimeMillis(),
                realTransport = false
            )
        }
        return try {
            val process = listOf(adbBinary, command).also {
                // Sicherheit: nur das geprüfte Kommando wird übergeben (keine Shell-Injection).
                require(it.size == 2)
            }.let {
                ProcessBuilder(it).redirectErrorStream(false).start()
            }
            val exit = process.waitFor()
            val out = process.inputStream.bufferedReader().readText().take(8000)
            val err = process.errorStream.bufferedReader().readText().take(8000)
            AdbResult(deviceId, command, exit, out, err, System.currentTimeMillis(), realTransport = true)
        } catch (e: Exception) {
            AdbResult(
                deviceId, command, null, "", "Transportfehler: ${e.message}",
                System.currentTimeMillis(), realTransport = false
            )
        }
    }

    private fun isBinaryAvailable(): Boolean = try {
        val process = ProcessBuilder(adbBinary, "version").redirectErrorStream(true).start()
        val exit = process.waitFor()
        process.inputStream.close()
        exit == 0
    } catch (e: Exception) {
        false
    }
}
