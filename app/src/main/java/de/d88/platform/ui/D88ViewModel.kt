package de.d88.platform.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.d88.platform.D88App
import de.d88.platform.agent.AgentTask
import de.d88.platform.core.D88Core
import de.d88.platform.debug.TransportState
import de.d88.platform.model.Role
import de.d88.platform.model.TrustState

/**
 * Zentrales ViewModel der D88-Oberfläche.
 *
 * Halteprinzip: Die UI ist eine dünne Schicht über dem D88-Core. Jede sichtbare
 * Entscheidung (Freigabe, Sperrung, Task, Audit-Status) stammt aus dem Core –
 * die UI erfindet nichts nach.
 */
class D88ViewModel(app: Application) : AndroidViewModel(app) {

    private val core: D88Core = (app as D88App).core
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableLiveData<List<UiDevice>>(emptyList())
    val devices: LiveData<List<UiDevice>> = _devices

    private val _approvals = MutableLiveData<List<UiApproval>>(emptyList())
    val approvals: LiveData<List<UiApproval>> = _approvals

    private val _audit = MutableLiveData<List<UiAudit>>(emptyList())
    val audit: LiveData<List<UiAudit>> = _audit

    private val _tasks = MutableLiveData<List<UiTask>>(emptyList())
    val tasks: LiveData<List<UiTask>> = _tasks

    private val _stats = MutableLiveData<UiStats>()
    val stats: LiveData<UiStats> = _stats

    private val _chatLines = MutableLiveData<List<ChatLine>>(emptyList())
    val chatLines: LiveData<List<ChatLine>> = _chatLines

    private val _chainStatus = MutableLiveData<String>("")
    val chainStatus: LiveData<String> = _chainStatus

    private val _toast = MutableLiveData<String>("")
    val toast: LiveData<String> = _toast

    private val chatLinesInternal = mutableListOf<ChatLine>()
    private var chatSeq = 0

    init {
        chatLinesInternal += ChatLine(
            "boot-${++chatSeq}", ChatLine.Who.D88,
            "D88 gestartet. Netzwerk: standardmäßig DENY. Transport-Status: ${transportLabel()}.",
            core.clock.now()
        )
        if (core.isSimulation) {
            chatLinesInternal += ChatLine(
                "boot-${++chatSeq}", ChatLine.Who.D88,
                "Achtung: SIMULATION – die angezeigten Geräte sind Demo-Daten, keine echten Geräte.",
                core.clock.now()
            )
        }
        refresh()
    }

    // ------------------------------------------------------------------ Aktionen

    /** Schickt eine Aufgabe an die Agent-Runtime (läuft über die Autorisierungskette). */
    fun submitTask(deviceId: String, intent: String, projectId: String? = null) {
        val device = core.registry.find(deviceId)
        if (device == null) {
            showToast("Unbekanntes Gerät: $deviceId")
            return
        }
        val session = try {
            core.ensureSession(device, projectId)
        } catch (e: Exception) {
            showToast("Session nicht möglich: ${e.message}")
            return
        }
        addChat(ChatLine.Who.USER, intent)
        val task = AgentTask(
            taskId = "task-${System.currentTimeMillis().toString(16)}",
            deviceId = deviceId,
            sessionId = session.sessionId,
            intent = intent,
            projectId = when (device.role) {
                Role.CLIENT -> projectId ?: device.projectScopes.firstOrNull()
                else -> projectId
            }
        )
        core.runtime.submit(task, object : de.d88.platform.agent.AgentRuntime.Listener {
            override fun onPlan(t: AgentTask, plan: de.d88.platform.agent.AgentPlan) {
                post {
                    addChat(ChatLine.Who.D88, "Plan erstellt: ${plan.steps.size} Schritte. Kette läuft…")
                    refresh()
                }
            }

            override fun onStepUpdated(t: AgentTask, outcome: de.d88.platform.agent.AgentRuntime.TaskOutcome) {
                post {
                    for (step in outcome.steps) {
                        when (step.state) {
                            de.d88.platform.agent.AgentRuntime.StepState.WAITING_HUMAN ->
                                addChat(ChatLine.Who.GATE, "FREIGABE ERFORDERT: ${step.step.description} [${step.detail}]")
                            else -> addChat(ChatLine.Who.D88, "Schritt „${step.step.description}“: ${step.state.name}")
                        }
                    }
                    refresh()
                }
            }

            override fun onFinished(t: AgentTask, outcome: de.d88.platform.agent.AgentRuntime.TaskOutcome) {
                post {
                    val summary = outcome.steps.joinToString(", ") { it.state.name }
                    addChat(ChatLine.Who.D88, "Task abgeschlossen. Zustände: $summary")
                    refresh()
                }
            }
        })
        app.saveState()
    }

    /** Human Gate: FREIGEBEN. */
    fun approve(actionId: String) {
        core.executor.execute {
            try {
                val outcome = core.runtime.resumeAfterApproval(actionId, approver = "admin")
                post {
                    if (outcome.isSuccess) {
                        val o = outcome.getOrThrow()
                        addChat(ChatLine.Who.GATE, "FREIGEGEBEN: ${o.step.description} → ${o.state.name}")
                    } else {
                        addChat(ChatLine.Who.GATE, "Freigabe fehlgeschlagen: ${outcome.exceptionOrNull()?.message}")
                    }
                    app.saveState()
                    refresh()
                }
            } catch (e: Exception) {
                post { addChat(ChatLine.Who.GATE, "Keine offene Freigabe: ${e.message}"); refresh() }
            }
        }
    }

    /** Human Gate: ABLEHNEN. */
    fun deny(actionId: String, reason: String) {
        core.executor.execute {
            try {
                val outcome = core.runtime.denyAction(actionId, approver = "admin", reason = reason)
                post {
                    if (outcome.isSuccess) {
                        addChat(ChatLine.Who.GATE, "ABGELEHNT: ${outcome.getOrThrow().step.description} ($reason)")
                    }
                    app.saveState()
                    refresh()
                }
            } catch (e: Exception) {
                post { addChat(ChatLine.Who.GATE, "Ablehnung fehlgeschlagen: ${e.message}"); refresh() }
            }
        }
    }

    /** Geräteverwaltungsaktion (wird selbst auditiert). */
    enum class DeviceAction { SUSPEND, REVOKE, BLOCK, REMOVE, ROLE_CLIENT, ROLE_GAST, TRUSTED }

    fun deviceAction(deviceId: String, action: DeviceAction) {
        val result = when (action) {
            DeviceAction.SUSPEND -> core.deviceManager.suspend(deviceId)
            DeviceAction.REVOKE -> core.deviceManager.revoke(deviceId)
            DeviceAction.BLOCK -> core.deviceManager.block(deviceId)
            DeviceAction.REMOVE -> core.deviceManager.remove(deviceId)
            DeviceAction.ROLE_CLIENT -> core.deviceManager.changeRole(deviceId, Role.CLIENT)
            DeviceAction.ROLE_GAST -> core.deviceManager.changeRole(deviceId, Role.GAST)
            DeviceAction.TRUSTED -> core.deviceManager.setTrustState(deviceId, TrustState.TRUSTED)
        }
        if (result.isSuccess) {
            showToast("Gerät aktualisiert: $deviceId ($action)")
        } else {
            showToast("Aktion abgelehnt: ${result.exceptionOrNull()?.message}")
        }
        app.saveState()
        refresh()
    }

    /** Verifiziert die Audit-Hash-Kette. */
    fun verifyChain() {
        val (ok, brokenId) = core.audit.verifyChain()
        if (ok) {
            _chainStatus.value = "Audit-Kette VERIFIZIERT (${core.audit.count} Events, Hash-Intaktheit ok)"
        } else {
            _chainStatus.value = "WARNUNG: Kette ab Event $brokenId beschädigt"
        }
        showToast(_chainStatus.value ?: "")
    }

    // ------------------------------------------------------------------ intern

    private fun post(block: () -> Unit) = mainHandler.post(block)

    private fun addChat(who: ChatLine.Who, text: String) {
        chatLinesInternal += ChatLine("line-${++chatSeq}", who, text, core.clock.now())
        if (chatLinesInternal.size > 200) chatLinesInternal.removeAt(0)
        _chatLines.value = chatLinesInternal.toList()
    }

    private fun showToast(text: String) {
        if (text.isNotBlank()) _toast.value = text
    }

    private fun transportLabel(): String = when (core.adbAdapter.state()) {
        TransportState.AVAILABLE -> "ADB-Transport verfügbar (echter Transport)"
        TransportState.UNAVAILABLE -> "ADB-Transport NICHT verfügbar (keine Fake-Ausführung)"
        TransportState.CONNECTING -> "ADB-Transport wird verbunden"
        TransportState.ERROR -> "ADB-Transportfehler"
    }

    private fun refresh() {
        val devices = core.registry.all()
        _devices.value = devices.map { d ->
            UiDevice.from(d, core.registry.sessionsForDevice(d.deviceId).count { s ->
                s.isGranted(core.clock.now())
            })
        }
        _approvals.value = core.gate.pending().map { p ->
            UiApproval(
                actionId = p.action.actionId,
                deviceId = p.action.deviceId,
                role = p.action.role,
                projectId = p.action.projectId,
                taskId = p.action.taskId,
                action = p.action.requestedChange,
                data = p.action.dataFlow.name,
                network = p.action.networkFlow,
                change = p.action.requestedChange,
                risk = p.result.finalRisk.name,
                expiresAt = p.expiresAt
            )
        }
        _audit.value = core.audit.tail(80).map { UiAudit.from(it) }
        _tasks.value = core.runtime.allOutcomes().map { UiTask.from(it) }
        val all = devices
        _stats.value = UiStats(
            deviceCount = all.size,
            adminCount = all.count { it.role == Role.ADMIN },
            clientCount = all.count { it.role == Role.CLIENT },
            guestCount = all.count { it.role == Role.GAST },
            pendingApprovals = core.gate.pendingCount,
            auditCount = core.audit.count,
            activeSessions = core.registry.allSessions().count { it.isGranted(core.clock.now()) },
            policyVersion = core.policy.version,
            networkDefault = "DENY",
            transportState = transportLabel(),
            simulation = core.isSimulation
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Core lebt in der App; Executor wird dort nicht beendet (Daemon-Thread).
    }
}
