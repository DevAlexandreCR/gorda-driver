package gorda.go.ui.about

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import gorda.go.BuildConfig
import gorda.go.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private lateinit var textVersion: TextView
    private lateinit var textPrivacy: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        textVersion = binding.textVersion
        textPrivacy = binding.textPrivacy

        textVersion.text = BuildConfig.VERSION_NAME
        textPrivacy.movementMethod = LinkMovementMethod.getInstance()

        return binding.root
    }
}