package com.siri.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.siri.assistant.ui.HomeScreen
import com.siri.assistant.ui.MainViewModel
import com.siri.assistant.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_WAKE_WORD_ACTIVATED = "com.siri.assistant.ACTION_WAKE_WORD"
    }

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.engine.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and allow display over lockscreen when activated hands-free
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        requestRequiredPermissions()
        handleIntent(intent)

        setContent {
            var currentScreen by remember { mutableStateOf("home") }

            when (currentScreen) {
                "settings" -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = "home" }
                    )
                }
                else -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateSettings = { currentScreen = "settings" }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically activate voice listening when user opens or returns to app
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.engine.startListening()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_WAKE_WORD_ACTIVATED,
            Intent.ACTION_ASSIST,
            Intent.ACTION_VOICE_COMMAND -> {
                viewModel.handleWakeWordTrigger()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
