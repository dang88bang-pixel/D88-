package de.d88.platform.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.d88.platform.R

/**
 * Live Observatory: kausaler Event-Stream des Causal Audit
 * (USER_REQUEST → TASK_CREATED → … → CONTEXT_UPDATE), Task-Status
 * und Kettenverifikation.
 */
class ObservatoryFragment : Fragment() {

    private val vm: D88ViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_observatory, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val auditAdapter = AuditAdapter()
        view.findViewById<RecyclerView>(R.id.audit_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = auditAdapter
        }
        vm.audit.observe(viewLifecycleOwner) { auditAdapter.submit(it) }

        val tasksAdapter = TasksAdapter()
        view.findViewById<RecyclerView>(R.id.tasks_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = tasksAdapter
        }
        vm.tasks.observe(viewLifecycleOwner) { tasksAdapter.submit(it) }

        vm.chainStatus.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.chain_status).text = it
        }
        view.findViewById<MaterialButton>(R.id.btn_verify).setOnClickListener {
            vm.verifyChain()
        }
    }
}
