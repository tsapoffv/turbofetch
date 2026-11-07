package com.turbofetch

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turbofetch.ui.files.FilesScreen
import com.turbofetch.ui.files.FilesViewModel
import com.turbofetch.ui.queue.QueueScreen
import com.turbofetch.ui.queue.QueueViewModel
import com.turbofetch.ui.settings.SettingsScreen
import com.turbofetch.ui.settings.SettingsViewModel
import com.turbofetch.ui.theme.TurboFetchTheme

class MainActivity : ComponentActivity() {

    private val queueViewModel: QueueViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val filesViewModel: FilesViewModel by viewModels()

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
                var tab by rememberSaveable { mutableStateOf(Tab.QUEUE) }
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

                if (showSettings) {
                    // Настройки — экран поверх: назад возвращает на вкладку.
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { showSettings = false },
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == Tab.QUEUE,
                                    onClick = { tab = Tab.QUEUE },
                                    icon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    },
                                    label = { Text(Tab.QUEUE.title) },
                                )
                                NavigationBarItem(
                                    selected = tab == Tab.FILES,
                                    onClick = { tab = Tab.FILES },
                                    icon = {
                                        Icon(Icons.Default.Folder, contentDescription = null)
                                    },
                                    label = { Text(Tab.FILES.title) },
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { showSettings = true },
                                    icon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    },
                                    label = { Text("Настройки") },
                                )
                            }
                        },
                    ) { padding ->
                        when (tab) {
                            Tab.QUEUE -> QueueScreen(
                                viewModel = queueViewModel,
                                fileNameTemplate = settings.fileNameTemplate,
                                onOpenSettings = { showSettings = true },
                                modifier = Modifier.padding(padding),
                            )

                            Tab.FILES -> FilesScreen(
                                viewModel = filesViewModel,
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
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

/** Вкладки нижней навигации. */
enum class Tab(val title: String) {
    QUEUE("Очередь"),
    FILES("Скачанное"),
}
