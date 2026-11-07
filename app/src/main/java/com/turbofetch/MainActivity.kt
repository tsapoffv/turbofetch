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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turbofetch.ui.files.FilesScreen
import com.turbofetch.ui.files.FilesViewModel
import com.turbofetch.ui.queue.QueueScreen
import com.turbofetch.ui.queue.QueueViewModel
import com.turbofetch.ui.settings.SettingsScreen
import com.turbofetch.ui.settings.SettingsViewModel
import com.turbofetch.ui.shrine.ShrineScreen
import com.turbofetch.ui.shrine.ShrineViewModel
import com.turbofetch.ui.theme.TurboFetchTheme

class MainActivity : ComponentActivity() {

    private val queueViewModel: QueueViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val shrineViewModel: ShrineViewModel by viewModels()
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
                    AppScaffold(
                        tab = tab,
                        onTabSelected = { tab = it },
                        onOpenSettings = { showSettings = true },
                    ) { contentPadding ->
                        when (tab) {
                            Tab.QUEUE -> QueueScreen(
                                viewModel = queueViewModel,
                                fileNameTemplate = settings.fileNameTemplate,
                                modifier = Modifier.padding(contentPadding),
                            )

                            Tab.FILES -> FilesScreen(
                                viewModel = filesViewModel,
                                modifier = Modifier.padding(contentPadding),
                            )

                            Tab.SHRINE -> ShrineScreen(
                                viewModel = shrineViewModel,
                                modifier = Modifier.padding(contentPadding),
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
    SHRINE("Святилище"),
}

/**
 * Каркас приложения: содержимое сверху, переключатель вкладок снизу.
 *
 * Панель вкладок — не Material-овская, а нарисованная: на бумаге
 * кнопки не нужны, достаточно двух подчёркнутых слов.
 */
@Composable
private fun AppScaffold(
    tab: Tab,
    onTabSelected: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    // История вкладок: «назад» возвращает на предыдущую, а не выходит из
    // приложения. Пустая история — значит, мы на первой вкладке.
    // Храним имена вкладок, а не сами вкладки: enum не переживает
    // сохранение состояния activity, а строка — переживает.
    val history = rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun goTo(new: Tab) {
        if (new == tab) return
        history.value = history.value + tab.name
        onTabSelected(new)
    }

    fun goBack() {
        val previous = history.value.lastOrNull() ?: return
        history.value = history.value.dropLast(1)
        val target = Tab.entries.firstOrNull { it.name == previous } ?: return
        onTabSelected(target)
    }

    BackHandler(enabled = history.value.isNotEmpty()) { goBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content(PaddingValues(top = 48.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TabBar(tab = tab, onTabSelected = ::goTo, onOpenSettings = onOpenSettings)
    }
}

@Composable
private fun TabBar(
    tab: Tab,
    onTabSelected: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { candidate ->
                val selected = candidate == tab
                Column(
                    modifier = Modifier
                        .clickable { onTabSelected(candidate) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = candidate.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(if (selected) 24.dp else 0.dp)
                            .height(1.5.dp)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.Transparent
                                },
                            ),
                    )
                }
                Spacer(Modifier.width(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onOpenSettings() },
            )
        }
    }
}
