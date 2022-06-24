package gorda.driver.ui.service.current

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import gorda.driver.databinding.FragmentCurrentServiceBinding

class CurrentServiceFragment : Fragment() {

    private var _binding: FragmentCurrentServiceBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val currentServiceViewModel =
            ViewModelProvider(this).get(CurrentServiceViewModel::class.java)

        _binding = FragmentCurrentServiceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textSlideshow
        currentServiceViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}