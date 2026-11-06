package com.ehan.curatcoret.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** One finished stroke. [xy] = x0, y0, x1, y1, ... in world coordinates. */
class InkStroke(val xy: FloatArray) {
    val path: Path = Path().also { p ->
        if (xy.size >= 2) {
            p.moveTo(xy[0], xy[1])
            var i = 2
            while (i + 1 < xy.size) {
                p.lineTo(xy[i], xy[i + 1])
                i += 2
            }
        }
    }

    companion object {
        fun fromPoints(points: List<Offset>): InkStroke {
            val arr = FloatArray(points.size * 2)
            points.forEachIndexed { i, p ->
                arr[i * 2] = p.x
                arr[i * 2 + 1] = p.y
            }
            return InkStroke(arr)
        }
    }
}

/** Full snapshot of one drawing (what gets written to disk). */
class DrawingData(
    val id: String,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val strokes: List<InkStroke>,
)

/** Lightweight row shown in the history list. */
data class HistoryEntry(
    val id: String,
    val updatedAt: Long,
    val strokeCount: Int,
)

internal fun List<Offset>.toStrokePath(): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(this[0].x, this[0].y)
    for (i in 1 until size) path.lineTo(this[i].x, this[i].y)
    return path
}
