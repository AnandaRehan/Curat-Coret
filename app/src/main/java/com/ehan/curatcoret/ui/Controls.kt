package com.ehan.curatcoret.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ehan.curatcoret.R
import com.ehan.curatcoret.ui.theme.AppColors
import com.ehan.curatcoret.ui.theme.CuratCoretTheme

/** Translucent "glass" panel (rgba(15,23,42,.85) + 1px white/10% border). */
fun Modifier.glass(shape: Shape): Modifier =
    this
        .background(AppColors.Glass, shape)
        .border(1.dp, AppColors.GlassBorder, shape)

@Composable
fun StatusPill(
    modeLabel: String,
    zoomPercent: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.status_format, modeLabel, zoomPercent),
        color = AppColors.Blue400,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * 54dp emoji button. [container] == null -> glass background (side controls);
 * otherwise a solid colour (bottom bar). [active] gives the blue glow.
 */
@Composable
fun ToolButton(
    emoji: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color? = null,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "toolButtonPress",
    )

    val surface = when {
        active -> Modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = AppColors.Blue500.copy(alpha = 0.4f),
                spotColor = AppColors.Blue500.copy(alpha = 0.4f),
            )
            .background(AppColors.Blue500, shape)
        container != null -> Modifier.background(container, shape)
        else -> Modifier.glass(shape)
    }

    Box(
        modifier = modifier
            .size(54.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(surface)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
fun BottomNav(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(24.dp))
            .padding(12.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun NavDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(AppColors.Slate700),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF020617)
@Composable
private fun ControlsPreview() {
    CuratCoretTheme {
        Box(Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusPill(modeLabel = "GAMBAR", zoomPercent = 100)
                BottomNav {
                    ToolButton("✏️", "Gambar", {}, container = AppColors.Slate800, active = true)
                    ToolButton("🖐️", "Geser", {}, container = AppColors.Slate800)
                    NavDivider()
                    ToolButton("🗑️", "Hapus", {}, container = AppColors.DangerSoft)
                }
            }
        }
    }
}
