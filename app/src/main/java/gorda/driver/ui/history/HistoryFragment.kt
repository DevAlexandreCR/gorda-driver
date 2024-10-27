package gorda.driver.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.databinding.FragmentHistoryBinding
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.NumberHelper

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val viewmodel: HistoryViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var textSummary: TextView
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.setLoading(true)
        viewmodel.getServices().addOnCompleteListener { _ ->
            mainViewModel.setLoading(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val historyList: RecyclerView = binding.historyList
        textSummary = binding.textSummary

        viewmodel.summary.observe(viewLifecycleOwner) {
            textSummary.text = NumberHelper.toCurrency(it, true)
        }

        with(historyList) {
            viewmodel.serviceList.observe(viewLifecycleOwner) {
                adapter = HistoryRecyclerViewAdapter(it) {service ->
                    val dialog = ServiceDialogFragment(service)
                    dialog.show(childFragmentManager, ServiceDialogFragment::javaClass.toString())
                }
            }
            layoutManager = LinearLayoutManager(context)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}