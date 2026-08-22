package com.app.snapmind.presentation.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.app.snapmind.presentation.onboarding.OnboardingScreen
import com.app.snapmind.presentation.settings.SettingsScreen
import com.app.snapmind.presentation.settings.SettingsViewModel
import com.app.snapmind.service.foreground.ScreenshotObserverService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 enforces edge-to-edge for apps targeting API 35.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)

        // Idempotent: keeps the daily job alive across updates and reboots.
        settingsViewModel.ensureScheduled()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
                    var showOnboarding by remember { mutableStateOf(false) }
                    var showSettings by remember { mutableStateOf(false) }

                    when {
                        // null means DataStore has not answered yet: render nothing rather
                        // than flashing onboarding at a returning user.
                        onboardingDone == null -> Unit

                        onboardingDone == false || showOnboarding -> OnboardingScreen(
                            onDone = {
                                showOnboarding = false
                                startObserverIfPossible()
                            }
                        )

                        showSettings -> SettingsScreen(
                            onBack = { showSettings = false }
                        )

                        else -> MainScreen(
                            onFixPermissions = { showOnboarding = true },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Task 3 field finding: a reinstall silently revoked everything. Re-check every time.
        viewModel.refreshPermissions()
        startObserverIfPossible()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Starting the service without media access would produce a permanent notification for a
     * pipeline that cannot see anything, so it is gated on the permission actually being there.
     */
    private fun startObserverIfPossible() {
        lifecycleScope.launch {
            if (viewModel.permissions.value.canCapture) {
                runCatching { ScreenshotObserverService.start(this@MainActivity) }
            }
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        val imageUri = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ->
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (imageUri == null && text.isNullOrBlank()) return
        viewModel.onShared(imageUri?.toString(), text)
    }
}
