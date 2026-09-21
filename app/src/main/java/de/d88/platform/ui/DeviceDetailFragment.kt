package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import de.d88.platform.R
import de.d88.platform.core.RoleEngine

/**
 * Geräteansicht (Spec Abschnitt 15/16): Übersicht, Rolle, Vertrauen, Sessions,
 * Projekte, Capabilities, Audit + Verwaltungsaktionen (auditiert).
 */
class DeviceDetailFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()
    private lateinit var deviceId: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_device_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceId = requireArguments().getString(ARG_DEVICE_ID).orEmpty()

        renderAudit(view)

        vm.devices.observe(viewLifecycleOwner) { devices ->
            val device = devices.firstOrNull { it.deviceId == deviceId } ?: return@observe
            renderTexts(view, device)
        }
        wireActions(view)
    }

    private fun renderTexts(view: View, device: UiDevice) {
        view.findViewById<TextView>(R.id.detail_headline).text =
            "${device.deviceType} · ${device.deviceId}"
            view.findViewById<TextView>(R.id.detail_role).text =
                "Rolle: ${device.role.name}"
            view.findViewById<TextView>(R.id.detail_trust).text =
                "Vertrauensstatus: ${device.trustLabel}"
            view.findViewById<TextView>(R.id.detail_platform).text =
                "${device.platformVersion} · D88 ${detailD88Version()}"
            view.findViewById<TextView>(R.id.detail_baseline).text =
                RoleEngine.baseline(device.role).joinToString("\n") { "• $it" }
            view.findViewById<TextView>(R.id.detail_projects).text =
                "Projekte: ${device.projectScopes.joinToString().ifBlank { "–" }}"
            view.findViewById<TextView>(R.id.detail_capabilities).text =
                "Capabilities: ${device.capabilities.joinToString()}"
        view.findViewById<TextView>(R.id.detail_sessions).text =
            "Aktive Sessions: ${device.activeSessions}"
        view.findViewById<TextView>(R.id.detail_simulated).visibility =
            if (device.simulated) View.VISIBLE else View.GONE
    }
    }

    private fun renderAudit(view: View) {
        // Audit-Einträge des Geräts als Liste (ohne zusätzliche Adapter-Komplexität).
        val auditAdapter = AuditAdapter()
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.device_audit_list).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            this.adapter = auditAdapter
        }
        vm.audit.observe(viewLifecycleOwner) { events ->
            val list = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.device_audit_list)
            (list.adapter as? AuditAdapter)?.submit(events.filter { it.deviceId == deviceId })
        }
    }

    private fun detailD88Version(): String = "0.1.0"

    private fun wireActions(view: View) {
        fun bind(id: Int, action: D88ViewModel.DeviceAction) {
            view.findViewById<MaterialButton>(id).setOnClickListener {
                vm.deviceAction(deviceId, action)
            }
        }
        bind(R.id.btn_suspend, D88ViewModel.DeviceAction.SUSPEND)
        bind(R.id.btn_block, D88ViewModel.DeviceAction.BLOCK)
        bind(R.id.btn_revoke, D88ViewModel.DeviceAction.REVOKE)
        bind(R.id.btn_remove, D88ViewModel.DeviceAction.REMOVE)
        bind(R.id.btn_role_client, D88ViewModel.DeviceAction.ROLE_CLIENT)
        bind(R.id.btn_role_gast, D88ViewModel.DeviceAction.ROLE_GAST)
    }

    companion object {
        private const val ARG_DEVICE_ID = "device_id"
        fun newInstance(deviceId: String): DeviceDetailFragment =
            DeviceDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_DEVICE_ID, deviceId) }
            }
    }
}
