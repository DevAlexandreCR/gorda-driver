package gorda.go.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gorda.go.R
import gorda.go.ui.MainViewModel

class HistoryFragment : Fragment() {

    private val viewmodel: HistoryViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

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
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history_list, container, false)

        if (view is RecyclerView) {
            with(view) {
                viewmodel.serviceList.observe(viewLifecycleOwner) {
                    adapter = HistoryRecyclerViewAdapter(it) {service ->
                        val dialog = ServiceDialogFragment(service)
                        dialog.show(childFragmentManager, ServiceDialogFragment::javaClass.toString())
                    }
                }
                layoutManager = LinearLayoutManager(context)
            }
        }
        return view
    }
}