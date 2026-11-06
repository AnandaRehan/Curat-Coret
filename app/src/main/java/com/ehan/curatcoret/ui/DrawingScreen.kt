package com.ehan.curatcoret.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ehan.curatcoret.CanvasViewModel
import com.ehan.curatcoret.R
import com.ehan.curatcoret.ToolMode
import com.ehan.curatcoret.ui.theme.AppColors
import kotlin.math.roundToInt

@Composable
fun DrawingScreen(viewModel: CanvasViewModel = viewModel()) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    val modeLabel = stringResource(
        if (viewModel.mode == ToolMode.DRAW) R.string.mode_draw else R.string.mode_pan,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background),
    ) {
        DrawingCanvas(viewModel = viewModel, modifier = Modifier.fillMaxSize())

        // UI layer: only the buttons intercept touches, the rest falls through to the canvas.
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            StatusPill(
                modeLabel = modeLabel,
                zoomPercent = (viewModel.scale * 100).roundToInt(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 15.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolButton(
                    emoji = "➕",
                    description = stringResource(R.string.cd_zoom_in),
                    onClick = { viewModel.zoomBy(1.2f) },
                )
                ToolButton(
                    emoji = "🎯",
                    description = stringResource(R.string.cd_reset_view),
                    onClick = viewModel::resetView,
                )
                ToolButton(
                    emoji = "➖",
                    description = stringResource(R.string.cd_zoom_out),
                    onClick = { viewModel.zoomBy(0.8f) },
                )
            }

            BottomNav(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
            ) {
                ToolButton(
                    emoji = "✏️",
                    description = stringResource(R.string.cd_mode_draw),
                    onClick = { viewModel.selectMode(ToolMode.DRAW) },
                    container = AppColors.Slate800,
                    active = viewModel.mode == ToolMode.DRAW,
                )
                ToolButton(
                    emoji = "🖐️",
                    description = stringResource(R.string.cd_mode_pan),
                    onClick = { viewModel.selectMode(ToolMode.PAN) },
                    container = AppColors.Slate800,
                    active = viewModel.mode == ToolMode.PAN,
                )
                NavDivider()
                ToolButton(
                    emoji = "🕘",
                    description = stringResource(R.string.cd_history),
                    onClick = {
                        viewModel.refreshHistory()
                        showHistory = true
                    },
                    container = AppColors.Slate800,
                )
                ToolButton(
                    emoji = "🗑️",
                    description = stringResource(R.string.cd_clear_all),
                    onClick = { showClearDialog = true },
                    container = AppColors.DangerSoft,
                )
            }
        }
    }

    if (showHistory) {
        HistorySheet(
            entries = viewModel.history,
            currentId = viewModel.currentDrawingId,
            onDismiss = { showHistory = false },
            onOpen = {
                viewModel.openDrawing(it)
                showHistory = false
            },
            onDelete = viewModel::deleteDrawing,
            onNew = {
                viewModel.newDrawing()
                showHistory = false
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_dialog_title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
