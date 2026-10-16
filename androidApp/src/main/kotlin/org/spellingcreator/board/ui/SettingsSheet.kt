package org.spellingcreator.board.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.spellingcreator.board.R
import org.spellingcreator.board.feedback.VoiceOption
import org.spellingcreator.board.input.CommitMode
import org.spellingcreator.board.settings.BoardSettings
import org.spellingcreator.board.settings.BoardStyle

private const val REFRACTORY_STEP_MILLIS = 50L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: BoardSettings,
    voices: List<VoiceOption>,
    onChange: (BoardSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.look), style = MaterialTheme.typography.titleMedium)
                val styles = listOf(BoardStyle.STENCIL, BoardStyle.PLAIN)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    styles.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = settings.style == style,
                            onClick = { onChange(settings.copy(style = style)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size),
                        ) {
                            Text(
                                when (style) {
                                    BoardStyle.STENCIL -> stringResource(R.string.look_stencil)
                                    BoardStyle.PLAIN -> stringResource(R.string.look_plain)
                                },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.commit_mode), style = MaterialTheme.typography.titleMedium)
                val modes = listOf(CommitMode.ON_TOUCH_DOWN, CommitMode.ON_TOUCH_UP)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.commitMode == mode,
                            onClick = { onChange(settings.copy(commitMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) {
                            Text(
                                when (mode) {
                                    CommitMode.ON_TOUCH_DOWN -> stringResource(R.string.commit_on_touch_down)
                                    CommitMode.ON_TOUCH_UP -> stringResource(R.string.commit_on_touch_up)
                                },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.repeat_window), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.refractoryMillis == 0L) {
                            stringResource(R.string.repeat_window_off)
                        } else {
                            stringResource(R.string.repeat_window_value, settings.refractoryMillis)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                val range = BoardSettings.MIN_REFRACTORY_MILLIS.toFloat()..BoardSettings.MAX_REFRACTORY_MILLIS.toFloat()
                Slider(
                    value = settings.refractoryMillis.toFloat(),
                    onValueChange = { value ->
                        val snapped = (value / REFRACTORY_STEP_MILLIS).toLong() * REFRACTORY_STEP_MILLIS
                        onChange(settings.copy(refractoryMillis = snapped))
                    },
                    valueRange = range,
                )
            }

            ToggleRow(
                title = stringResource(R.string.stylus_only),
                subtitle = stringResource(R.string.stylus_only_hint),
                checked = settings.stylusOnly,
                onCheckedChange = { onChange(settings.copy(stylusOnly = it)) },
            )
            VoicePicker(
                voices = voices,
                selectedId = settings.voiceId,
                onSelect = { onChange(settings.copy(voiceId = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.letter_sounds),
                checked = settings.letterSounds,
                onCheckedChange = { onChange(settings.copy(letterSounds = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.speak_words),
                checked = settings.speakWords,
                onCheckedChange = { onChange(settings.copy(speakWords = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.haptics),
                checked = settings.haptics,
                onCheckedChange = { onChange(settings.copy(haptics = it)) },
            )
        }
    }
}

/** The system voice for letters and words, with a way to the system screen where more can be installed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    voices: List<VoiceOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val automatic = stringResource(R.string.voice_automatic)
    val selectedLabel = voices.firstOrNull { it.id == selectedId }?.let { "${it.name}, ${it.quality}" } ?: automatic
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.voice), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(automatic) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text("${voice.name}, ${voice.quality}") },
                        onClick = {
                            onSelect(voice.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.voice_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                val intent = Intent(TTS_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        ) {
            Text(stringResource(R.string.open_tts_settings))
        }
    }
}

private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
