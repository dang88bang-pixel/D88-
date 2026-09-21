package de.d88.platform

import android.app.Application
import de.d88.platform.core.D88Core
import de.d88.platform.data.JsonStorage
import de.d88.platform.model.Clock

/**
 * D88 Application – startet den Core, stellt Persistenz wieder her
 * oder legt (im Prototyp) die klar gekennzeichnete SIMULATION an.
 */
class D88App : Application() {

    val storage: JsonStorage by lazy { JsonStorage(this) }
    val core: D88Core by lazy { D88Core(Clock.SYSTEM) }

    override fun onCreate() {
        super.onCreate()
        val storedDevices = storage.loadDevices()
        if (storedDevices.isNotEmpty()) {
            storedDevices.forEach { core.registry.register(it) }
            core.audit.restore(storage.loadAudit())
        } else {
            core.seedSimulationIfEmpty()
            saveState()
        }
    }

    /** Persistiert Registry + Audit (lokal, keine Cloud). */
    fun saveState() {
        runCatching {
            storage.saveDevices(core.registry.all())
            storage.saveAudit(core.audit.all)
        }
    }
}
