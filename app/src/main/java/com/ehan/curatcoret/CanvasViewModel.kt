package com.ehan.curatcoret

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel

enum class ToolMode { DRAW, PAN }

class CanvasViewModel : ViewModel() {

    var mode by mutableStateOf(ToolMode.DRAW)
        private set

    /** Zoom factor (1f = 100%). */
    var scale by mutableFloatStateOf(1f)
        private set

    /** Pan offset in screen pixels. */
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Canvas size in pixels, used as the zoom pivot (screen center). */
    var canvasSize: Size = Size.Zero

    /** Finished strokes, stored in world coordinates. */
    val strokes = mutableStateListOf<Path>()

    /** Strokes in progress, one per finger (multi-touch). */
    val activeStrokes = mutableStateMapOf<Long, SnapshotStateList<Offset>>()

    private val lastPointerPos = HashMap<Long, Offset>()

    fun setMode(newMode: ToolMode) {
        mode = newMode
    }

    /** Screen pixels -> world coordinates (1 world unit = 1dp at 100% zoom). */
    private fun worldPos(screen: Offset, density: Float): Offset =
        (screen - offset) / (scale * density)

    fun onPointerDown(id: Long, pos: Offset, density: Float) {
        lastPointerPos[id] = pos
        if (mode == ToolMode.DRAW) {
            activeStrokes[id] = mutableStateListOf(worldPos(pos, density))
        }
    }

    fun onPointerMove(id: Long, pos: Offset, density: Float) {
        val last = lastPointerPos[id] ?: return
        when (mode) {
            ToolMode.DRAW -> activeStrokes[id]?.add(worldPos(pos, density))
            ToolMode.PAN -> offset += pos - last
        }
        lastPointerPos[id] = pos
    }

    fun onPointerUp(id: Long) {
        activeStrokes.remove(id)?.let { points ->
            if (points.size >= 2) strokes.add(points.toStrokePath())
        }
        lastPointerPos.remove(id)
    }

    fun endAllPointers() {
        activeStrokes.keys.toList().forEach(::onPointerUp)
        lastPointerPos.clear()
    }

    fun zoomBy(factor: Float) {
        val previous = scale
        val next = (previous * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = next / previous
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        offset = center - (center - offset) * ratio
        scale = next
    }

    fun resetView() {
        scale = 1f
        offset = Offset.Zero
    }

    fun clearAll() {
        strokes.clear()
        activeStrokes.clear()
    }

    private companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 10f
    }
}

internal fun List<Offset>.toStrokePath(): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(this[0].x, this[0].y)
    for (i in 1 until size) path.lineTo(this[i].x, this[i].y)
    return path
}