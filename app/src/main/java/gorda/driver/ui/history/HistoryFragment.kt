package gorda.driver.ui.history

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import gorda.driver.R

class HistoryFragment : Fragment() {

    private val viewmodel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewmodel.getServices()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history_list, container, false)

        if (view is RecyclerView) {
            with(view) {
                viewmodel.serviceList.observe(viewLifecycleOwner) {
                    adapter = HistoryRecyclerViewAdapter(it)
                }
                layoutManager = LinearLayoutManager(context)
            }
        }
        return view
    }
}