package gorda.driver.ui.service.apply

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import gorda.driver.R

class ApplyFragment : Fragment() {

    companion object {
        fun newInstance() = ApplyFragment()
    }

    private val viewModel: ApplyViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_apply, container, false)
    }
}