package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import de.d88.platform.R

/**
 * D88 Dashboard: Systemstatus, Geräteübersicht (Rolle + Vertrauen),
 * Freigabe-Counter, Simulation-Banner.
 */
class DashboardFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.stats.observe(viewLifecycleOwner) { s ->
            view.findViewById<TextView>(R.id.stat_devices).text =
                s.deviceCount.toString()
            view.findViewById<TextView>(R.id.stat_roles).text =
                "ADMIN ${s.adminCount} · CLIENT ${s.clientCount} · GAST ${s.guestCount}"
            view.findViewById<TextView>(R.id.stat_approvals).text =
                s.pendingApprovals.toString()
            view.findViewById<TextView>(R.id.stat_sessions).text = s.activeSessions.toString()
            view.findViewById<TextView>(R.id.stat_policy).text =
                "v${s.policyVersion} · Netzwerk: ${s.networkDefault}"
            view.findViewById<TextView>(R.id.stat_transport).text = s.transportState
            view.findViewById<MaterialCardView>(R.id.simulation_banner).visibility =
                if (s.simulation) View.VISIBLE else View.GONE
        }

        val devicesAdapter = DevicesAdapter(
            onClick = { device ->
                (activity as MainActivity).show(
                    DeviceDetailFragment.newInstance(device.deviceId)
                )
            }
        )
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.devices_list)
            .apply {
                setHasFixedSize(false)
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                adapter = devicesAdapter
            }
        vm.devices.observe(viewLifecycleOwner) { devicesAdapter.submit(it) }

        view.findViewById<MaterialButton>(R.id.btn_security).setOnClickListener {
            (activity as MainActivity).show(SecurityFragment())
        }
        view.findViewById<MaterialButton>(R.id.btn_knowledge).setOnClickListener {
            (activity as MainActivity).show(KnowledgeFragment())
        }
    }
}
