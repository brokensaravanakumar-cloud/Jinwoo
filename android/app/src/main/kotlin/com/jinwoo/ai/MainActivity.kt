package com.jinwoo.ai

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jinwoo.ai.assistant.AssistantViewModel
import com.jinwoo.ai.assistant.SettingsViewModel
import com.jinwoo.ai.ui.JinWooRoot

class MainActivity : ComponentActivity() {
    private val assistant: AssistantViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val container get() = (application as JinWooApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { JinWooRoot(assistant, settingsVm) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() { super.onStart(); container.appInForeground = true }
    override fun onStop() { container.appInForeground = false; super.onStop() }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_AUTO_LISTEN, false) == true) {
            i.removeExtra(EXTRA_AUTO_LISTEN)
            assistant.activate(fromWake = true)
        }
    }

    companion object { const val EXTRA_AUTO_LISTEN = "auto_listen" }
}
