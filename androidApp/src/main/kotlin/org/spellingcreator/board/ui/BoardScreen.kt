package org.spellingcreator.board.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.spellingcreator.board.R
import org.spellingcreator.board.feedback.LetterVoice
import org.spellingcreator.board.layout.BoardLayouts
import org.spellingcreator.board.session.FeedbackKind
import org.spellingcreator.board.session.HapticKind

/** How long a cell stays lit after it was chosen or noticed. Long enough to be seen, short enough not to lag. */
private const val FLASH_MILLIS = 350L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(viewModel: BoardViewModel = viewModel()) {
    val state by viewModel.state
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val flashes = remember { mutableStateMapOf<String, FlashKind>() }
    var popped by remember { mutableStateOf<String?>(null) }
    var popCount by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    val voice = remember { LetterVoice(context) }
    DisposableEffect(voice) { onDispose { voice.release() } }
    LaunchedEffect(state.layout.id, state.settings.voiceId) {
        voice.select(state.settings.voiceId)
        voice.prepare(state.layout.keys.map { it.spoken })
    }

    DisposableEffect(viewModel) {
        viewModel.feedbackSink = { feedback ->
            feedback.haptic?.let { kind ->
                haptics.performHapticFeedback(
                    when (kind) {
                        HapticKind.CONFIRM -> HapticFeedbackType.Confirm
                        HapticKind.REJECT -> HapticFeedbackType.Reject
                    },
                )
            }
            when (feedback.kind) {
                FeedbackKind.SAY -> voice.say(feedback.text.orEmpty(), feedback.interrupt)
                FeedbackKind.KEY_COMMITTED ->
                    feedback.keyId?.let { keyId ->
                        popped =
                            viewModel.session.layout
                                .key(keyId)
                                ?.label
                        popCount++
                        scope.flash(flashes, keyId, FlashKind.Committed)
                    }
                FeedbackKind.KEY_REPEATED ->
                    feedback.keyId?.let { keyId -> scope.flash(flashes, keyId, FlashKind.Repeated) }
                else -> Unit
            }
        }
        onDispose { viewModel.feedbackSink = null }
    }

    // A tablet dimming mid-sentence is a real problem with slow spellers, so the board keeps the screen on.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val sentence = state.text.trim()
    val copiedMessage = stringResource(R.string.copied)
    val shareTitle = stringResource(R.string.share)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        enabled = sentence.isNotEmpty(),
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText(shareTitle, sentence))
                            viewModel.recordSentence()
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
                    }
                    IconButton(
                        enabled = sentence.isNotEmpty(),
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, sentence)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                            viewModel.recordSentence()
                        },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = shareTitle)
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        // The board gets as much room as possible. In landscape the chrome moves into a side column
        // so the slab can use the full height; in portrait it stacks above and below the board.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            val board: @Composable (Modifier) -> Unit = { boardModifier ->
                BoardCanvas(
                    state = state,
                    flashes = flashes,
                    onResize = viewModel::resize,
                    onPointer = viewModel::onPointer,
                    modifier = boardModifier,
                )
            }
            val sideWidth = maxWidth * 0.28f
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        modifier =
                            Modifier
                                .width(sideWidth)
                                .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LayoutPicker(selectedId = state.layout.id, onSelect = viewModel::selectLayout)
                        SentenceStrip(text = state.text, popped = popped, popCount = popCount, maxLines = 8)
                        Spacer(Modifier.weight(1f))
                        ControlButtons(viewModel = viewModel, grid = true)
                    }
                    board(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LayoutPicker(selectedId = state.layout.id, onSelect = viewModel::selectLayout)
                    SentenceStrip(text = state.text, popped = popped, popCount = popCount)
                    board(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    ControlButtons(viewModel = viewModel, grid = false)
                }
            }
        }
    }

    if (showHistory) {
        HistorySheet(
            entries = state.history,
            enabled = state.settings.keepHistory,
            onShareAll = {
                val send =
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, viewModel.historyText)
                context.startActivity(Intent.createChooser(send, shareTitle))
            },
            onDeleteAll = viewModel::clearHistory,
            onSpeak = viewModel::speakHistoryEntry,
            onRestore = { id ->
                viewModel.restoreHistoryEntry(id)
                showHistory = false
            },
            onDelete = viewModel::deleteHistoryEntry,
            onDismiss = { showHistory = false },
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            voices = remember { voice.availableVoices() },
            onChange = viewModel::updateSettings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun LayoutPicker(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BoardLayouts.all.forEachIndexed { index, layout ->
            SegmentedButton(
                selected = layout.id == selectedId,
                onClick = { onSelect(layout.id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = BoardLayouts.all.size),
            ) {
                Text(layout.name)
            }
        }
    }
}

/** Delete, Space, Speak and Clear: one row of four, or two rows of two beside the board. */
@Composable
private fun ControlButtons(
    viewModel: BoardViewModel,
    grid: Boolean,
) {
    val delete: @Composable (Modifier) -> Unit = {
        ControlButton(
            label = stringResource(R.string.delete),
            icon = { Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null) },
            onClick = viewModel::backspace,
            modifier = it,
        )
    }
    val space: @Composable (Modifier) -> Unit = {
        ControlButton(
            label = stringResource(R.string.space),
            icon = { Icon(Icons.Outlined.SpaceBar, contentDescription = null) },
            onClick = viewModel::space,
            modifier = it,
        )
    }
    val speak: @Composable (Modifier) -> Unit = {
        ControlButton(
            label = stringResource(R.string.speak),
            icon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
            onClick = viewModel::speak,
            modifier = it,
        )
    }
    val clear: @Composable (Modifier) -> Unit = {
        ControlButton(
            label = stringResource(R.string.clear),
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            onClick = viewModel::clear,
            modifier = it,
        )
    }
    if (grid) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                delete(Modifier.weight(1f))
                space(Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                speak(Modifier.weight(1f))
                clear(Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            delete(Modifier.weight(1f))
            space(Modifier.weight(1f))
            speak(Modifier.weight(1f))
            clear(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SentenceStrip(
    text: String,
    popped: String?,
    popCount: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val scale by animateFloatAsState(
        targetValue = if (popCount > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_sentence),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = text,
                    style =
                        MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                        ),
                    modifier = Modifier.weight(1f),
                    maxLines = maxLines,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (popped != null) {
                    PopLetter(label = popped, key = popCount, scale = scale)
                }
            }
        }
    }
}

@Composable
private fun PopLetter(
    label: String,
    key: Int,
    scale: Float,
) {
    var visible by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        visible = true
        delay(FLASH_MILLIS * 2)
        visible = false
    }
    if (visible) {
        Text(
            text = label,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
private fun ControlButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.flash(
    flashes: MutableMap<String, FlashKind>,
    keyId: String,
    kind: FlashKind,
) {
    flashes[keyId] = kind
    launch {
        delay(FLASH_MILLIS)
        if (flashes[keyId] == kind) flashes.remove(keyId)
    }
}
