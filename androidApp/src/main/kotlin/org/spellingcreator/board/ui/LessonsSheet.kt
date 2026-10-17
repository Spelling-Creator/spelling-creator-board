package org.spellingcreator.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.spellingcreator.board.R
import org.spellingcreator.board.lessons.LessonSummary
import org.spellingcreator.board.lessons.PracticeLinkState
import org.spellingcreator.board.lessons.PracticeLinkStatus

/** Pick a lesson from the hub, or paste a link to one, and open it on the board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonsSheet(
    hubUrl: String,
    listing: List<LessonSummary>?,
    loading: Boolean,
    error: String?,
    onLoadListing: () -> Unit,
    onOpen: (String) -> Unit,
    linkState: PracticeLinkState,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var link by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(hubUrl) { onLoadListing() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.lessons), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.lessons_from, hubUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.lesson_link)) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onOpen(link) }, enabled = link.isNotBlank() && !loading) {
                    Text(stringResource(R.string.open))
                }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Text(stringResource(R.string.session_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.session_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (linkState.isActive) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sessionStatusText(linkState), modifier = Modifier.weight(1f))
                    Button(onClick = onLeave) { Text(stringResource(R.string.session_leave)) }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.session_code)) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onJoin(code) }, enabled = code.isNotBlank()) {
                        Text(stringResource(R.string.session_join))
                    }
                }
                if (linkState.status == PracticeLinkStatus.FAILED && linkState.message != null) {
                    Text(
                        linkState.message!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            when {
                listing == null && loading -> SkeletonRows()
                listing != null && listing.isEmpty() -> Text(stringResource(R.string.lessons_none))
                listing != null ->
                    LazyColumn {
                        items(listing, key = { it.id }) { lesson ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !loading) { onOpen(lesson.id) }
                                        .padding(vertical = 12.dp),
                            ) {
                                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOf(
                                        lesson.author,
                                        pluralStringResource(
                                            R.plurals.sections,
                                            lesson.sectionCount,
                                            lesson.sectionCount,
                                        ),
                                    ).filter { it.isNotBlank() }.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
            }
        }
    }
}

@Composable
fun sessionStatusText(state: PracticeLinkState): String =
    when (state.status) {
        PracticeLinkStatus.CONNECTING -> stringResource(R.string.session_connecting)
        PracticeLinkStatus.WAITING -> stringResource(R.string.session_waiting)
        PracticeLinkStatus.LINKED -> stringResource(R.string.session_linked, state.code)
        PracticeLinkStatus.FAILED -> state.message ?: stringResource(R.string.session_failed)
        PracticeLinkStatus.OFF -> ""
    }

/** Placeholder rows while the listing loads, the shape of the rows that will replace them. */
@Composable
private fun SkeletonRows() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                )
            }
            HorizontalDivider()
        }
    }
}
