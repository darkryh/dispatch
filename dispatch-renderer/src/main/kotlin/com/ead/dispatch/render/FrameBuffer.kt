package com.ead.dispatch.render

import kotlin.text.iterator

/**
 * A double-buffered frame buffer for flicker-free terminal rendering.
 *
 * This class maintains two buffers:
 * - currentBuffer: What is currently displayed on screen
 * - nextBuffer: What will be displayed after the next render
 *
 * Only the differences between buffers are sent to the terminal,
 * minimizing flicker and improving performance.
 */
class FrameBuffer(
    initialWidth: Int = 80,
    initialHeight: Int = 24,
) {
    /**
     * The current buffer (what's on screen).
     */
    private var currentBuffer: Array<CharArray> = createBuffer(initialWidth, initialHeight)

    /**
     * The next buffer (what we're building).
     */
    private var nextBuffer: Array<CharArray> = createBuffer(initialWidth, initialHeight)

    /**
     * Current buffer width.
     */
    var width: Int = initialWidth
        private set

    /**
     * Current buffer height.
     */
    var height: Int = initialHeight
        private set

    /**
     * Clear the next buffer.
     */
    fun clear() {
        for (row in nextBuffer) {
            row.fill(' ')
        }
    }

    /**
     * Resize the buffers if needed.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth != width || newHeight != height) {
            width = newWidth
            height = newHeight
            currentBuffer = createBuffer(newWidth, newHeight)
            nextBuffer = createBuffer(newWidth, newHeight)
        }
    }

    /**
     * Write text at a position in the next buffer.
     *
     * @param x Column (0-indexed).
     * @param y Row (0-indexed).
     * @param text Text to write.
     */
    fun write(x: Int, y: Int, text: String) {
        if (y < 0 || y >= height) return

        var col = x
        for (char in text) {
            if (col in 0 until width) {
                nextBuffer[y][col] = char
            }
            col++
        }
    }

    /**
     * Write lines starting at a position.
     *
     * @param x Starting column.
     * @param y Starting row.
     * @param lines Lines to write.
     */
    fun writeLines(x: Int, y: Int, lines: List<String>) {
        for ((index, line) in lines.withIndex()) {
            write(x, y + index, line)
        }
    }

    /**
     * Calculate the differences between current and next buffer.
     *
     * @return List of changes needed to update the display.
     */
    fun diff(): List<FrameChange> {
        val changes = mutableListOf<FrameChange>()

        for (y in 0 until height) {
            val currentRow = currentBuffer[y]
            val nextRow = nextBuffer[y]

            // Find ranges that differ
            var x = 0
            while (x < width) {
                // Find start of difference
                while (x < width && currentRow[x] == nextRow[x]) x++
                if (x >= width) break

                val startX = x

                // Find end of difference
                while (x < width && currentRow[x] != nextRow[x]) x++

                // Extract the changed text
                val text = String(nextRow.copyOfRange(startX, x))
                changes.add(FrameChange(startX, y, text))
            }
        }

        return changes
    }

    /**
     * Swap buffers after rendering.
     */
    fun swap() {
        val temp = currentBuffer
        currentBuffer = nextBuffer
        nextBuffer = temp
        // Clear the new "next" buffer
        clear()
    }

    /**
     * Get a full frame render (for initial display).
     */
    fun fullFrame(): List<String> {
        return nextBuffer.map { String(it) }
    }

    /**
     * Copy current buffer state to next buffer.
     *
     * Useful when only part of the screen changed.
     */
    fun copyCurrentToNext() {
        for (y in 0 until height) {
            nextBuffer[y] = currentBuffer[y].copyOf()
        }
    }

    private companion object {
        fun createBuffer(width: Int, height: Int): Array<CharArray> {
            return Array(height) { CharArray(width) { ' ' } }
        }
    }
}

/**
 * A change to be applied to the frame.
 */
data class FrameChange(
    /**
     * X position (column).
     */
    val x: Int,
    /**
     * Y position (row).
     */
    val y: Int,
    /**
     * Text to write at this position.
     */
    val text: String,
)
