package org.spellingcreator.board.input

enum class PointerPhase {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}

enum class PointerKind {
    FINGER,
    STYLUS,
    MOUSE,
    UNKNOWN,
}

/**
 * One raw touch event from the platform, in board coordinates.
 *
 * @property pointerId Identifies one finger or stylus for the life of its touch.
 * @property timeMillis A monotonic clock in milliseconds. Only differences between events matter.
 */
data class PointerEvent(
    val pointerId: Long,
    val phase: PointerPhase,
    val x: Float,
    val y: Float,
    val timeMillis: Long,
    val kind: PointerKind = PointerKind.FINGER,
)
