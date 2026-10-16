package org.spellingcreator.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.spellingcreator.board.R
import org.spellingcreator.board.input.PointerEvent
import org.spellingcreator.board.input.PointerKind
import org.spellingcreator.board.input.PointerPhase
import org.spellingcreator.board.layout.BoardGeometry
import org.spellingcreator.board.layout.CellRect
import org.spellingcreator.board.layout.StencilFrame
import org.spellingcreator.board.session.BoardState
import org.spellingcreator.board.settings.BoardStyle

enum class FlashKind {
    Committed,
    Repeated,
}

/** Colors for the stencil look. The cutouts show the screen background, so only the slab changes with the theme. */
private val stencilAmber = Color(0xFFFFC53D)
private val stencilOrange = Color(0xFFFF7A59)

/**
 * Draws the board and forwards raw touches to the session.
 *
 * Geometry and hit testing live in the shared module; this only paints and reports pointers. It
 * reads every pointer change on the initial pass so nothing above it can steal or delay a touch.
 */
@Composable
fun BoardCanvas(
    state: BoardState,
    flashes: Map<String, FlashKind>,
    onResize: (width: Float, height: Float, gap: Float, padding: Float) -> Unit,
    onPointer: (PointerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 10.dp.toPx() }
    val cornerPx = with(density) { 14.dp.toPx() }
    val strokePx = with(density) { 3.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.board_content_description)
    val style = state.settings.style

    val plain =
        PlainColors(
            cell = colors.surfaceContainerHighest,
            label = colors.onSurface,
            pressed = colors.secondaryContainer,
            committed = colors.primary,
            onCommitted = colors.onPrimary,
            repeated = colors.tertiary,
        )
    val stencil =
        StencilColors(
            slab = colors.onBackground,
            cutout = colors.background,
            pressed = colors.surfaceVariant,
            committed = stencilAmber,
            repeated = stencilOrange,
        )

    // The geometry depends on the style as well as the size, so both changes go through here.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // Where the slab sits inside the canvas in stencil mode. Touches are reported relative to it.
    val slab =
        if (style == BoardStyle.STENCIL && canvasSize != IntSize.Zero) {
            StencilFrame.fit(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else {
            null
        }
    val slabOffset = rememberUpdatedState(if (slab == null) Offset.Zero else Offset(slab.left, slab.top))
    LaunchedEffect(style, canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        if (slab == null) {
            onResize(width, height, gapPx, 0f)
        } else {
            val frame = StencilFrame(slab.width, slab.height)
            onResize(frame.hitWidth, slab.height, 0f, frame.margin)
        }
    }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .onSizeChanged { size -> canvasSize = size }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            for (change in event.changes) {
                                val phase =
                                    when {
                                        change.changedToDownIgnoreConsumed() -> PointerPhase.DOWN
                                        change.changedToUpIgnoreConsumed() -> PointerPhase.UP
                                        change.pressed -> PointerPhase.MOVE
                                        else -> null
                                    } ?: continue
                                val offset = slabOffset.value
                                onPointer(
                                    PointerEvent(
                                        pointerId = change.id.value,
                                        phase = phase,
                                        x = change.position.x - offset.x,
                                        y = change.position.y - offset.y,
                                        timeMillis = change.uptimeMillis,
                                        kind =
                                            when (change.type) {
                                                PointerType.Stylus -> PointerKind.STYLUS
                                                PointerType.Mouse -> PointerKind.MOUSE
                                                PointerType.Touch -> PointerKind.FINGER
                                                else -> PointerKind.UNKNOWN
                                            },
                                    ),
                                )
                                change.consume()
                            }
                        }
                    }
                },
    ) {
        when (style) {
            BoardStyle.PLAIN -> {
                val geometry = BoardGeometry(state.layout, size.width, size.height, gapPx, 0f)
                drawPlain(geometry, state, flashes, plain, textMeasurer, cornerPx, strokePx)
            }
            BoardStyle.STENCIL -> {
                val box = StencilFrame.fit(size.width, size.height)
                val frame = StencilFrame(box.width, box.height)
                val geometry = BoardGeometry(state.layout, frame.hitWidth, box.height, 0f, frame.margin)
                translate(left = box.left, top = box.top) {
                    drawStencil(frame, geometry, state, flashes, stencil, textMeasurer, strokePx)
                }
            }
        }
    }
}

private class PlainColors(
    val cell: Color,
    val label: Color,
    val pressed: Color,
    val committed: Color,
    val onCommitted: Color,
    val repeated: Color,
)

private class StencilColors(
    val slab: Color,
    val cutout: Color,
    val pressed: Color,
    val committed: Color,
    val repeated: Color,
)

private fun DrawScope.drawPlain(
    geometry: BoardGeometry,
    state: BoardState,
    flashes: Map<String, FlashKind>,
    colors: PlainColors,
    textMeasurer: TextMeasurer,
    cornerPx: Float,
    strokePx: Float,
) {
    val labelSize = (geometry.cells.firstOrNull()?.height ?: 0f) * 0.48f
    val style = TextStyle(fontSize = labelSize.toSp(), fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    for (cell in geometry.cells) {
        val flash = flashes[cell.key.id]
        val isPressed = cell.key.id == state.pressedKeyId
        val fill =
            when {
                flash == FlashKind.Committed -> colors.committed
                isPressed -> colors.pressed
                else -> colors.cell
            }
        val topLeft = Offset(cell.left, cell.top)
        val cellSize = Size(cell.width, cell.height)
        drawRoundRect(color = fill, topLeft = topLeft, size = cellSize, cornerRadius = CornerRadius(cornerPx))
        if (flash == FlashKind.Repeated) {
            drawRoundRect(
                color = colors.repeated,
                topLeft = topLeft,
                size = cellSize,
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(width = strokePx * 2),
            )
        }
        val textColor = if (flash == FlashKind.Committed) colors.onCommitted else colors.label
        drawLabel(textMeasurer, cell, style, textColor)
    }
}

/**
 * The physical board: one dark slab with the handle and every letter cut out of it. The cutouts
 * are painted in the screen's background color, which is what shows through a real stencil.
 * Feedback lights the cutout: amber when a letter goes in, an orange ring when it was a repeat.
 */
private fun DrawScope.drawStencil(
    frame: StencilFrame,
    geometry: BoardGeometry,
    state: BoardState,
    flashes: Map<String, FlashKind>,
    colors: StencilColors,
    textMeasurer: TextMeasurer,
    strokePx: Float,
) {
    drawRoundRect(
        color = colors.slab,
        topLeft = Offset.Zero,
        size = Size(frame.width, frame.height),
        cornerRadius = CornerRadius(frame.cornerRadius),
    )
    frame.handle?.let { handle ->
        drawRoundRect(
            color = colors.cutout,
            topLeft = Offset(handle.left, handle.top),
            size = Size(handle.width, handle.height),
            cornerRadius = CornerRadius(handle.width / 2),
        )
    }
    val first = geometry.cells.firstOrNull() ?: return
    val labelSize = minOf(first.height * 0.7f, first.width * 0.8f)
    val style =
        TextStyle(
            fontSize = labelSize.toSp(),
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.sp,
        )
    val inset = minOf(first.width, first.height) * 0.08f
    for (cell in geometry.cells) {
        val flash = flashes[cell.key.id]
        val isPressed = cell.key.id == state.pressedKeyId
        val glowRadius = CornerRadius(minOf(cell.width, cell.height) * 0.18f)
        val glowTopLeft = Offset(cell.left + inset, cell.top + inset)
        val glowSize = Size(cell.width - inset * 2, cell.height - inset * 2)
        if (flash == FlashKind.Committed) {
            drawRoundRect(
                color = colors.committed.copy(alpha = 0.28f),
                topLeft = glowTopLeft,
                size = glowSize,
                cornerRadius = glowRadius,
            )
        } else if (flash == FlashKind.Repeated) {
            drawRoundRect(
                color = colors.repeated,
                topLeft = glowTopLeft,
                size = glowSize,
                cornerRadius = glowRadius,
                style = Stroke(width = strokePx * 2),
            )
        }
        val cutout =
            when {
                flash == FlashKind.Committed -> colors.committed
                isPressed -> colors.pressed
                else -> colors.cutout
            }
        drawLabel(textMeasurer, cell, style, cutout)
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    cell: CellRect,
    style: TextStyle,
    color: Color,
) {
    val layoutResult = textMeasurer.measure(cell.key.label, style)
    drawText(
        textLayoutResult = layoutResult,
        color = color,
        topLeft =
            Offset(
                cell.centerX - layoutResult.size.width / 2f,
                cell.centerY - layoutResult.size.height / 2f,
            ),
    )
}
