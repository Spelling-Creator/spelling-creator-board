package org.spellingcreator.board.input

import org.spellingcreator.board.layout.BoardGeometry
import org.spellingcreator.board.layout.BoardKey

/** When a touch counts as choosing a key. */
enum class CommitMode {
    /** The moment the finger lands, like touching a physical board. The default. */
    ON_TOUCH_DOWN,

    /** When the finger lifts, from wherever it is then. */
    ON_TOUCH_UP,
}

enum class SelectionKind {
    /** Nothing to report for this event. */
    NONE,

    /** A key was chosen. */
    COMMITTED,

    /** The same key again inside the refractory window. Not added. */
    REPEATED,

    /** In touch-up mode, the finger is resting on a key. Visual only. */
    PRESSED,

    /** The finger lifted or the touch was canceled without choosing anything new. */
    RELEASED,

    /** The touch was on the board view but not on the board. */
    OFF_BOARD,

    /** A second finger, or a finger while in stylus-only mode. */
    IGNORED,
}

data class Selection(
    val kind: SelectionKind,
    val key: BoardKey? = null,
) {
    companion object {
        val none = Selection(SelectionKind.NONE)
        val released = Selection(SelectionKind.RELEASED)
        val offBoard = Selection(SelectionKind.OFF_BOARD)
        val ignored = Selection(SelectionKind.IGNORED)
    }
}

/**
 * Turns raw pointer events into key selections.
 *
 * The rules that make the board feel like a physical one live here:
 * - First touch wins. While one pointer is down, every other pointer is ignored, so a steadying palm
 *   or the partner's hand cannot add letters.
 * - A key is committed on touch-down by default, so feedback arrives the instant the finger lands.
 * - The same key hit again inside [refractoryMillis] is reported as [SelectionKind.REPEATED] rather
 *   than added twice, so an uncertain second press gets a clear answer instead of a doubled letter.
 *   Double letters still work by waiting out the window.
 * - In stylus-only mode, fingers are ignored entirely.
 *
 * The engine keeps no clock of its own; it trusts the timestamps on the events.
 */
class SelectionEngine(
    var commitMode: CommitMode = CommitMode.ON_TOUCH_DOWN,
    var refractoryMillis: Long = 400,
    var stylusOnly: Boolean = false,
) {
    private var activePointerId: Long? = null
    private var pressedKeyId: String? = null
    private var lastCommittedKeyId: String? = null
    private var lastCommitTime: Long = Long.MIN_VALUE

    val isPressed: Boolean get() = activePointerId != null

    fun reset() {
        activePointerId = null
        pressedKeyId = null
        lastCommittedKeyId = null
        lastCommitTime = Long.MIN_VALUE
    }

    fun handle(
        event: PointerEvent,
        geometry: BoardGeometry,
    ): Selection =
        when (event.phase) {
            PointerPhase.DOWN -> down(event, geometry)
            PointerPhase.MOVE -> move(event, geometry)
            PointerPhase.UP -> up(event, geometry)
            PointerPhase.CANCEL -> cancel(event)
        }

    private fun down(
        event: PointerEvent,
        geometry: BoardGeometry,
    ): Selection {
        if (stylusOnly && event.kind != PointerKind.STYLUS) return Selection.ignored
        if (activePointerId != null) return Selection.ignored
        activePointerId = event.pointerId
        val cell = geometry.cellAt(event.x, event.y)
        if (cell == null) {
            pressedKeyId = null
            return Selection.offBoard
        }
        return when (commitMode) {
            CommitMode.ON_TOUCH_DOWN -> commit(cell.key, event.timeMillis)
            CommitMode.ON_TOUCH_UP -> {
                pressedKeyId = cell.key.id
                Selection(SelectionKind.PRESSED, cell.key)
            }
        }
    }

    private fun move(
        event: PointerEvent,
        geometry: BoardGeometry,
    ): Selection {
        if (event.pointerId != activePointerId) return Selection.ignored
        if (commitMode == CommitMode.ON_TOUCH_DOWN) return Selection.none
        val cell = geometry.cellAt(event.x, event.y)
        val keyId = cell?.key?.id
        if (keyId == pressedKeyId) return Selection.none
        pressedKeyId = keyId
        return if (cell == null) Selection.released else Selection(SelectionKind.PRESSED, cell.key)
    }

    private fun up(
        event: PointerEvent,
        geometry: BoardGeometry,
    ): Selection {
        if (event.pointerId != activePointerId) return Selection.ignored
        activePointerId = null
        pressedKeyId = null
        if (commitMode == CommitMode.ON_TOUCH_DOWN) return Selection.released
        val cell = geometry.cellAt(event.x, event.y) ?: return Selection.offBoard
        return commit(cell.key, event.timeMillis)
    }

    private fun cancel(event: PointerEvent): Selection {
        if (event.pointerId != activePointerId) return Selection.ignored
        activePointerId = null
        pressedKeyId = null
        return Selection.released
    }

    private fun commit(
        key: BoardKey,
        timeMillis: Long,
    ): Selection {
        val isRepeat = key.id == lastCommittedKeyId && timeMillis - lastCommitTime < refractoryMillis
        if (isRepeat) return Selection(SelectionKind.REPEATED, key)
        lastCommittedKeyId = key.id
        lastCommitTime = timeMillis
        return Selection(SelectionKind.COMMITTED, key)
    }
}
