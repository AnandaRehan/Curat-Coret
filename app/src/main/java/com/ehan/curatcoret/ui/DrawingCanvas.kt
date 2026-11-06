package com.ehan.curatcoret.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.ehan.curatcoret.CanvasViewModel
import com.ehan.curatcoret.toStrokePath
import com.ehan.curatcoret.ui.theme.AppColors
import kotlin.math.floor

private const val GRID_SPACING = 80f
private const val GRID_DOT_RADIUS = 1.5f
private const val STROKE_WIDTH_DP = 3f

@Composable
fun DrawingCanvas(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .onSizeChanged { viewModel.canvasSize = it.toSize() }
            .pointerInput(viewModel) {
                // Every finger is tracked independently (multi-touch drawing / panning).
                awaitEachGesture {
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                val id = change.id.value
                                when {
                                    change.changedToDown() ->
                                        viewModel.onPointerDown(id, change.position, density)
                                    change.changedToUp() ->
                                        viewModel.onPointerUp(id)
                                    change.pressed ->
                                        viewModel.onPointerMove(id, change.position, density)
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        viewModel.endAllPointers()
                    }
                }
            },
    ) {
        val scale = viewModel.scale
        val offset = viewModel.offset
        val k = density * scale // world unit -> pixel

        drawRect(AppColors.Background)

        // Dot grid
        val left = -offset.x / k
        val top = -offset.y / k
        val right = left + size.width / k
        val bottom = top + size.height / k
        var x = floor(left / GRID_SPACING) * GRID_SPACING
        while (x < right) {
            var y = floor(top / GRID_SPACING) * GRID_SPACING
            while (y < bottom) {
                drawCircle(
                    color = AppColors.GridDot,
                    radius = GRID_DOT_RADIUS * k,
                    center = Offset(x * k + offset.x, y * k + offset.y),
                )
                y += GRID_SPACING
            }
            x += GRID_SPACING
        }

        // Strokes (constant on-screen thickness regardless of zoom)
        val style = Stroke(
            width = STROKE_WIDTH_DP / scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        withTransform({
            translate(offset.x, offset.y)
            scale(k, k, pivot = Offset.Zero)
        }) {
            viewModel.strokes.forEach { path ->
                drawPath(path, AppColors.Stroke, style = style)
            }
            viewModel.activeStrokes.values.forEach { points ->
                if (points.size >= 2) {
                    drawPath(points.toStrokePath(), AppColors.Stroke, style = style)
                }
            }
        }
    }
}