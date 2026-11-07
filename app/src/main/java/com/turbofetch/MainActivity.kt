package com.turbofetch

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turbofetch.domain.link.LinkParser
import com.turbofetch.ui.files.FilesScreen
import com.turbofetch.ui.files.FilesViewModel
import com.turbofetch.ui.queue.QueueScreen
import com.turbofetch.ui.queue.QueueViewModel
import com.turbofetch.ui.settings.SettingsScreen
import com.turbofetch.ui.settings.SettingsViewModel
import com.turbofetch.ui.shrine.ShrineScreen
import com.turbofetch.ui.shrine.ShrineViewModel
import com.turbofetch.ui.theme.TurboFetchTheme
import com.turbofetch.ui.theme.isBaybekod
import com.turbofetch.ui.theme.livingAccentBrush
import com.turbofetch.ui.theme.tabLabelStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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

        // Если ссылка пришла через «Поделиться» и юзер просил закрываться
        // сразу — интерфейс не рисуем вовсе, только добавляем ссылку в фоне.
        val closing = handleIncomingLink(intent)
        if (closing) {
            finish()
            return
        }
        requestNotificationPermissionIfNeeded()

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            TurboFetchTheme(
                theme = settings.theme,
                dark = settings.darkMode,
            ) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var tab by rememberSaveable { mutableStateOf(Tab.QUEUE) }

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
        if (handleIncomingLink(intent)) finish()
    }

    /**
     * Забирает ссылку из «Поделиться» или из клика по ссылке в браузере.
     * Интент очищаем, иначе при повторном показе activity ссылка добавится дважды.
     *
     * @return `true`, если activity закрывается и интерфейс рисовать не нужно.
     */
    private fun handleIncomingLink(intent: Intent?): Boolean {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                intent.removeExtra(Intent.EXTRA_TEXT)
                if (text == null) return false

                // Юзер просил не открывать приложение: ссылка уходит в очередь
                // в фоне, окно закрывается. Но только если ссылка опознана —
                // иначе ошибку будет некому показать.
                if (closeOnShareEnabled() && LinkParser.parse(text) != null) {
                    queueViewModel.addLinkDetached(text)
                    Toast.makeText(this, "Ссылка добавлена в очередь", Toast.LENGTH_SHORT).show()
                    return true
                }
                queueViewModel.addLink(text)
            }

            Intent.ACTION_VIEW -> {
                val data = intent.dataString?.takeIf { it.isNotBlank() }
                intent.data = null
                if (data == null) return false
                queueViewModel.addLink(data)
            }
        }
        return false
    }

    /**
     * Читает настройку синхронно: решать, закроемся ли мы, нужно до первой
     * отрисовки, иначе окно приложения мелькнёт. Чтение из локальной SQLite
     * занимает единицы миллисекунд, поэтому блокировка тут безопасна.
     */
    private fun closeOnShareEnabled(): Boolean = runBlocking(Dispatchers.IO) {
        (application as TurboFetchApp).container.settingsRepository.current().closeOnShare
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
        // MaterialTheme по умолчанию даёт LocalContentColor = чёрный.
        // На тёмной теме это значит чёрный текст на чёрном фоне, поэтому
        // цвет содержимого задаётся явно.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues(top = 48.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TabBar(tab = tab, onTabSelected = ::goTo, onOpenSettings = onOpenSettings)
        }
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
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Вкладки делят место поровну и поджимаются при крупном шрифте:
            // иначе длинные названия выдавливают «Настройки» в столбик.
            Tab.entries.forEach { candidate ->
                val selected = candidate == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(candidate) }
                        // Отступ между вкладками: без него подписи
                        // при крупном шрифте слипаются в «СкачанноеСвятилище».
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = candidate.title,
                        style = tabLabelStyle(),
                        color = if (selected) {
                            if (isBaybekod()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(if (selected) 24.dp else 0.dp)
                            .height(if (isBaybekod()) 3.dp else 1.5.dp)
                            .background(
                                if (selected) {
                                    if (isBaybekod()) {
                                        livingAccentBrush()
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.onSurface,
                                                MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
                                    }
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.Transparent),
                                    )
                                },
                            ),
                    )
                }
            }
            // Настройки — иконка: подпись при крупном шрифте не помещалась.
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
