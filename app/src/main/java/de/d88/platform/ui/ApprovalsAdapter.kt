package de.d88.platform.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.d88.platform.R

/** Human-Gate-Queue: Freigabe-Karten mit [ABLEHNEN] [FREIGEBEN]. */
class ApprovalsAdapter(
    private val onApprove: (String) -> Unit,
    private val onDeny: (String) -> Unit
) : RecyclerView.Adapter<ApprovalsAdapter.VH>() {

    private val items = mutableListOf<UiApproval>()

    fun submit(list: List<UiApproval>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_approval, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.header.text = "AKTION ERFORDERT FREIGABE"
        holder.body.text =
            "Gerät: ${a.deviceId}\n" +
                "Rolle: ${a.role.name}\n" +
                "Projekt: ${a.projectId ?: "–"}\n" +
                "Aufgabe: ${a.taskId ?: "–"}\n" +
                "Aktion: ${a.action}\n" +
                "Daten: ${a.data}\n" +
                "Netzwerk: ${if (a.network) "JA" else "nein"}\n" +
                "Risiko: ${a.risk}"
        holder.btnApprove.setOnClickListener { onApprove(a.actionId) }
        holder.btnDeny.setOnClickListener { onDeny(a.actionId) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.approval_header)
        val body: TextView = view.findViewById(R.id.approval_body)
        val btnApprove: MaterialButton = view.findViewById(R.id.btn_approve)
        val btnDeny: MaterialButton = view.findViewById(R.id.btn_deny)
    }
}
