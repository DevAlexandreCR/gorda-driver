package gorda.driver.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import gorda.driver.R
import gorda.driver.databinding.FragmentHistoryBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.NumberHelper

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val viewmodel: HistoryViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val binding get() = _binding!!
    private var isRefreshInProgress = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        refreshData(isUserRefresh = false)

        return binding.root
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
            binding.textTotalEarnings.text = NumberHelper.toCurrency(summary, false)
        }

         viewmodel.serviceList.observe(viewLifecycleOwner) { list ->
             binding.textTotalServices.text = list.size.toString()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.secondary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.primary_dark)
        binding.swipeRefreshLayout.setProgressViewOffset(
            false,
            0,
            resources.getDimensionPixelSize(R.dimen.map_margin_big)
        )
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            binding.historyList.canScrollVertically(-1)
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData(isUserRefresh = true)
        }
    }

    private fun refreshData(isUserRefresh: Boolean) {
        if (isRefreshInProgress) {
            if (isUserRefresh) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            return
        }

        isRefreshInProgress = true

        if (isUserRefresh) {
            binding.swipeRefreshLayout.isRefreshing = true
        } else {
            mainViewModel.setLoading(true)
        }

        viewmodel.getServices().addOnCompleteListener { _ ->
            finishRefresh(isUserRefresh)
        }.withTimeout {
            finishRefresh(isUserRefresh)
            mainViewModel.setErrorTimeout(true)
        }
    }

    private fun finishRefresh(isUserRefresh: Boolean) {
        isRefreshInProgress = false
        if (isUserRefresh) {
            _binding?.swipeRefreshLayout?.isRefreshing = false
        } else {
            mainViewModel.setLoading(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isRefreshInProgress = false
        _binding = null
    }
}
