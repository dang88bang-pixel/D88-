package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.d88.platform.R

/**
 * Security-Ansicht: Policy, Netzwerk-Deny-by-Default, lokale
 * Datenschutz-Defaults und Rollenmatrix.
 */
class SecurityFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_security, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.stats.observe(viewLifecycleOwner) { s ->
            view.findViewById<TextView>(R.id.sec_policy).text =
                "Policy-Version: ${s.policyVersion}\n" +
                    "Netzwerk-Default: ${s.networkDefault}\n" +
                    "Transport: ${s.transportState}\n" +
                    "Audit-Events: ${s.auditCount}\n" +
                    "Offene Freigaben: ${s.pendingApprovals}"
            view.findViewById<TextView>(R.id.sec_privacy).text =
                PRIVACY
            view.findViewById<TextView>(R.id.sec_matrix).text = MATRIX
        }
    }

    companion object {
        private const val PRIVACY =
            "Lokale Datenschutz-Defaults (lokal-first, nicht-kommerziell):\n\n" +
                "• Keine Cloud-Verbindungen – die App deklariert keine INTERNET-Permission\n" +
                "• Keine stillen Analytics-, Telemetrie- oder Crash-Verbindungen\n" +
                "• Keine unnötigen Hardwarefingerprints / Geräte-Sammlung\n" +
                "• NETWORK ist Capability und default DENY\n" +
                "• Daten bleiben lokal (filesDir), JSON-persistiert\n" +
                "• Audit-Hash-Kette lokal verifizierbar (SHA-256)"

        private const val MATRIX =
            "Rollenmatrix (Basis – konkrete Rechte erfordern immer\n" +
                "Role+Trust+Capability+Project+Task+Session+Policy):\n\n" +
                "ADMIN  → volle Verwaltung; CRITICAL immer Human Gate\n" +
                "CLIENT → Projekt-/Aufgaben-gebundene Nutzung; ab MEDIUM-HIGH Gate\n" +
                "GAST   → isolierter GUEST-Bereich; keine Admin/ADB/Deployment/Cloud"
    }
}
