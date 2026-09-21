package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.d88.platform.R

/**
 * Wissens-/RAG-Bereich: lokal indizierte D88-Konzepte + lokale Suche.
 * Rein lokal – kein Netzwerk (siehe docs: kb-network).
 */
class KnowledgeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_knowledge, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = KnowledgeAdapter()
        view.findViewById<RecyclerView>(R.id.knowledge_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        val input = view.findViewById<EditText>(R.id.kb_search)
        fun render(query: String) {
            adapter.submit(de.d88.platform.ui.KnowledgeBase.search(query))
            view.findViewById<TextView>(R.id.kb_hint).text =
                if (query.isBlank()) "${adapter.count} Dokumente (lokal)" else
                    "${adapter.count} Treffer (lokale Keyword-Suche)"
        }
        render("")
        view.findViewById<MaterialButton>(R.id.btn_kb_search).setOnClickListener {
            render(input.text.toString())
        }
    }
}
