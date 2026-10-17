package org.spellingcreator.board.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.spellingcreator.board.R
import org.spellingcreator.board.lessons.BlockKind
import org.spellingcreator.board.lessons.LessonStepKind
import org.spellingcreator.board.session.LessonView

/**
 * The open lesson's current step: a section's material, or one question with the board as its
 * answer box. The partner steps through it; the speller answers on the board and taps Done.
 */
@Composable
fun LessonPanel(
    view: LessonView,
    canSubmit: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeak: () -> Unit,
    speaking: Boolean,
    onStop: () -> Unit,
    onSubmit: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    maxBodyHeight: Dp = Dp.Unspecified,
    /** Shown under the title while the board follows a Practice page. */
    linkStatus: String? = null,
) {
    var showAnswers by remember(view.stepIndex) { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(view.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.lesson_step_of, view.stepIndex + 1, view.stepCount) +
                            if (view.sectionName.isNotBlank()) ", ${view.sectionName}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (linkStatus != null) {
                        Text(
                            linkStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (speaking) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.stop))
                    }
                } else {
                    IconButton(onClick = onSpeak) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = stringResource(R.string.lesson_read_aloud),
                        )
                    }
                }
                IconButton(onClick = onEnd) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.lesson_end))
                }
            }

            // The body takes what is left after the header and the buttons, never more, so the
            // buttons stay in view however long the step's text is.
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = maxBodyHeight)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (view.kind) {
                    LessonStepKind.CONTENT -> ContentBlocks(view)
                    LessonStepKind.QUESTION -> {
                        val question = view.question ?: return@Column
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SuggestionChip(onClick = {}, label = { Text("${question.tag} ${question.label}") })
                            Text(
                                stringResource(R.string.lesson_question_number, view.questionNumber),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(question.prompt, style = MaterialTheme.typography.titleLarge)
                        view.answer?.let { answer ->
                            Text(
                                stringResource(R.string.lesson_answered, answer),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (question.answers.isNotEmpty()) {
                            if (showAnswers) {
                                Text(
                                    stringResource(
                                        if (question.suggested) R.string.lesson_examples else R.string.lesson_answers,
                                        question.answers.joinToString(", "),
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                TextButton(
                                    onClick = { showAnswers = true },
                                ) { Text(stringResource(R.string.lesson_show_answer)) }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = view.hasPrevious) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.lesson_previous),
                    )
                }
                IconButton(onClick = onNext, enabled = view.hasNext) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(R.string.lesson_next),
                    )
                }
                if (view.kind == LessonStepKind.QUESTION) {
                    FilledTonalButton(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Text(stringResource(R.string.lesson_done), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentBlocks(view: LessonView) {
    for (block in view.blocks) {
        when (block.kind) {
            BlockKind.HEADING -> block.lines.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
            BlockKind.TEXT -> block.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            BlockKind.IMAGE ->
                block.lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            BlockKind.SPELLING ->
                Text(
                    block.lines.joinToString("   "),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            BlockKind.VAKT ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(2.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium)
                            .padding(12.dp),
                ) {
                    Text(
                        stringResource(R.string.lesson_vakt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    block.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
            BlockKind.QUESTION -> Unit
        }
    }
}
