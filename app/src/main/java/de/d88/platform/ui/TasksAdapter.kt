package de.d88.platform.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R

/** Task-Übersicht mit Schrittzuständen (Observatory). */
class TasksAdapter : RecyclerView.Adapter<TasksAdapter.VH>() {

    private val items = mutableListOf<UiTask>()

    fun submit(list: List<UiTask>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.intent.text = t.intent
        holder.meta.text = "Gerät: ${t.deviceId} · ${if (t.finished) "abgeschlossen" else "läuft"}"
        holder.steps.text = t.steps.joinToString("\n") {
            "– ${it.description} → ${it.state}"
        }.ifBlank { "– keine Schritte (Planung läuft) –" }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val intent: TextView = view.findViewById(R.id.task_intent)
        val meta: TextView = view.findViewById(R.id.task_meta)
        val steps: TextView = view.findViewById(R.id.task_steps)
    }
}
