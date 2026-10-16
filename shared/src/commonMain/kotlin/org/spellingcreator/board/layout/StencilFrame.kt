package org.spellingcreator.board.layout

/** A rectangle in view units. */
data class Box(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * The proportions of the physical stencil board, scaled to the rectangle the view gives it.
 *
 * The slab fills the rectangle. A handle slot is cut near the right edge, and the letters are cut out
 * of what is left, with the slab's margin as hit-test padding so a touch near the edge still snaps to
 * the nearest letter. Both apps draw from this so the two boards match.
 *
 * To lay the letters out: `session.resize(hitWidth, height, gap = 0, padding = margin)`. Cells then
 * come back in the same coordinates as the slab, and a touch on the handle lands past [hitWidth] and
 * is reported as off the board.
 */
class StencilFrame(
    val width: Float,
    val height: Float,
    val hasHandle: Boolean = true,
) {
    private val shortSide = minOf(width, height)

    /** Radius of the slab's corners. */
    val cornerRadius: Float = shortSide * 0.08f

    /** Space between the slab's edge and the letters. */
    val margin: Float = shortSide * 0.06f

    /** The handle slot, or null when the board has none. */
    val handle: Box? =
        if (hasHandle) {
            val handleWidth = width * 0.085f
            Box(
                left = width - margin - handleWidth,
                top = height * 0.12f,
                width = handleWidth,
                height = height * 0.76f,
            )
        } else {
            null
        }

    /** How much of the width the letters may use. Everything to the right is the handle. */
    val hitWidth: Float = handle?.let { it.left - margin / 2 } ?: width

    companion object {
        /** The physical board is about 5:4. The slab may stretch this far either way before it stops looking like one. */
        const val MIN_ASPECT: Float = 1.0f
        const val MAX_ASPECT: Float = 1.5f

        /**
         * The largest slab that fits the available rectangle, centered. The slab fills the rectangle
         * when its proportions are between [minAspect] and [maxAspect] (width to height); otherwise it
         * is clamped to the nearer bound, since a bigger board matters more than exact proportions.
         * Views draw the slab at the returned offset and subtract that offset from touches.
         */
        fun fit(
            availableWidth: Float,
            availableHeight: Float,
            minAspect: Float = MIN_ASPECT,
            maxAspect: Float = MAX_ASPECT,
        ): Box {
            val available = availableWidth.coerceAtLeast(0f) to availableHeight.coerceAtLeast(0f)
            var (width, height) = available
            val aspect = if (height == 0f) maxAspect else width / height
            if (aspect > maxAspect) width = height * maxAspect
            if (aspect < minAspect) height = width / minAspect
            return Box(
                left = (availableWidth - width) / 2,
                top = (availableHeight - height) / 2,
                width = width,
                height = height,
            )
        }
    }
}
