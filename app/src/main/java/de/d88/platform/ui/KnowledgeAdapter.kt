package de.d88.platform.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.d88.platform.R

/** Wissens-/RAG-Dokumente (lokal). */
class KnowledgeAdapter : RecyclerView.Adapter<KnowledgeAdapter.VH>() {

    private val items = mutableListOf<Pair<KnowledgeBase.Doc, Int>>()

    val count: Int
        get() = items.size

    fun submit(list: List<Pair<KnowledgeBase.Doc, Int>>) {
        items.clear()
        items += list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_knowledge, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (doc, score) = items[position]
        holder.title.text = doc.title + if (score > 0) "  (Treffer: $score)" else ""
        holder.body.text = doc.body
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.kb_title)
        val body: TextView = view.findViewById(R.id.kb_body)
    }
}
