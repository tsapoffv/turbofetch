package com.turbofetch

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turbofetch.ui.queue.QueueScreen
import com.turbofetch.ui.queue.QueueViewModel
import com.turbofetch.ui.settings.SettingsScreen
import com.turbofetch.ui.settings.SettingsViewModel
import com.turbofetch.ui.theme.TurboFetchTheme

class MainActivity : ComponentActivity() {

    private val queueViewModel: QueueViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIncomingLink(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            TurboFetchTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

                if (showSettings) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { showSettings = false },
                    )
                } else {
                    QueueScreen(
                        viewModel = queueViewModel,
                        fileNameTemplate = settings.fileNameTemplate,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLink(intent)
    }

    /**
     * Забирает ссылку из «Поделиться» или из клика по ссылке в браузере.
     * Интент очищаем, иначе при повторном показе activity ссылка добавится дважды.
     */
    private fun handleIncomingLink(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
                    queueViewModel.addLink(it)
                }
                intent.removeExtra(Intent.EXTRA_TEXT)
            }

            Intent.ACTION_VIEW -> {
                intent.dataString?.takeIf { it.isNotBlank() }?.let {
                    queueViewModel.addLink(it)
                }
                intent.data = null
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
