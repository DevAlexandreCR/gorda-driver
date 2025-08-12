package gorda.driver.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import gorda.driver.databinding.FragmentHistoryBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.NumberHelper

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val viewmodel: HistoryViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.setLoading(true)
        viewmodel.getServices().addOnCompleteListener { _ ->
            mainViewModel.setLoading(false)
        }.withTimeout {
            mainViewModel.setLoading(false)
            mainViewModel.setErrorTimeout(true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        return root
    }

    private fun setupRecyclerView() {
        with(binding.historyList) {
            layoutManager = LinearLayoutManager(context)
            viewmodel.serviceList.observe(viewLifecycleOwner) { services ->
                adapter = HistoryRecyclerViewAdapter(services) { service ->
                    val dialog = ServiceDialogFragment(service)
                    dialog.show(childFragmentManager, ServiceDialogFragment::class.java.toString())
                }

                // Show/hide empty state
                if (services.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.historyList.visibility = View.GONE
                } else {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.historyList.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupObservers() {
        viewmodel.summary.observe(viewLifecycleOwner) { summary ->
            binding.textSummaryPeriod.text = NumberHelper.toCurrency(summary, true)
            binding.textTotalEarnings.text = NumberHelper.toCurrency(summary, false)
        }

         viewmodel.serviceList.observe(viewLifecycleOwner) { list ->
             binding.textTotalServices.text = list.size.toString()
         }

         binding.textAverageRating.text = String.format("%.1f", 5.0)
    }

    private fun setupClickListeners() {

        // Refresh button
        binding.btnRefreshHistory.setOnClickListener {
            refreshData()
        }
    }

    private fun refreshData() {
        mainViewModel.setLoading(true)
        viewmodel.getServices().addOnCompleteListener { _ ->
            mainViewModel.setLoading(false)
        }.withTimeout {
            mainViewModel.setLoading(false)
            mainViewModel.setErrorTimeout(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}