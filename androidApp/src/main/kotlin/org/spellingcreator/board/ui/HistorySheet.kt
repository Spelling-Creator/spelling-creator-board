package org.spellingcreator.board.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.spellingcreator.board.R
import org.spellingcreator.board.history.HistoryEntry
import java.text.DateFormat
import java.util.Date

/** The sentences the speller has finished, newest first. Everything here can be deleted at any time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    enabled: Boolean,
    onShareAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onSpeak: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = onShareAll, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.history_share_all))
                    }
                    IconButton(onClick = { confirmDeleteAll = true }, enabled = entries.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.history_delete_all),
                        )
                    }
                }
            }
            when {
                !enabled -> EmptyNote(stringResource(R.string.history_off), stringResource(R.string.history_off_hint))
                entries.isEmpty() ->
                    EmptyNote(
                        stringResource(R.string.history_empty),
                        stringResource(R.string.history_empty_hint),
                    )
                else ->
                    LazyColumn {
                        items(entries, key = { it.id }) { entry ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onRestore(entry.id) }
                                        .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.text, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        formatter.format(Date(entry.timeMillis)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onSpeak(entry.id) }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = stringResource(R.string.history_speak_entry),
                                    )
                                }
                                IconButton(onClick = { onDelete(entry.id) }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.history_delete_entry),
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.history_delete_all_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        onDeleteAll()
                    },
                ) {
                    Text(stringResource(R.string.history_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyNote(
    title: String,
    hint: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
