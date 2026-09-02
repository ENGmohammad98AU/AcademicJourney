package com.academicjourney.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
private fun rememberButtonScale(interactionSource: MutableInteractionSource): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 620f),
        label = "buttonPressScale"
    )
    return scale
}

private fun Modifier.pressScale(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

@Composable
fun InteractiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberButtonScale(interactionSource)
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun InteractiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberButtonScale(interactionSource)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun InteractiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberButtonScale(interactionSource)
    TextButton(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content
    )
}
