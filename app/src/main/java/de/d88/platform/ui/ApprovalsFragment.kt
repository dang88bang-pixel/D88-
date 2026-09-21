package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R

/**
 * Human Gate (Spec Abschnitt 13): offene Freigaben mit der D88-UI:
 *
 *   ┌───────────────────────────────────┐
 *   │ AKTION ERFORDERT FREIGABE         │
 *   │ Gerät / Rolle / Projekt / Aufgabe │
 *   │ Aktion / Daten / Netzwerk         │
 *   │       [ ABLEHNEN ] [ FREIGEBEN ]  │
 *   └───────────────────────────────────┘
 */
class ApprovalsFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_approvals, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ApprovalsAdapter(
            onApprove = { vm.approve(it) },
            onDeny = { vm.deny(it, "manuell abgelehnt") }
        )
        view.findViewById<RecyclerView>(R.id.approvals_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        vm.approvals.observe(viewLifecycleOwner) { adapter.submit(it) }
    }
}
