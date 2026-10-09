package com.ai.agent.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.ai.agent.R

/**
 * SettingsFragment — launches the existing SettingsActivity (v6.0.0 stop-gap).
 * Full Fragment conversion in v6.1.0.
 */
class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_simple, container, false)
        view.findViewById<Button>(R.id.btnLaunch).apply {
            text = "Open Settings →"
            setOnClickListener {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
        return view
    }
}
