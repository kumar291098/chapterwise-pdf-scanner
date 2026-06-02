package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly interactive 3D page flip component that simulates a real-life book.
 * It automatically handles Portrait (Single Page) and Landscape (Double Page) modes.
 */
@Composable
fun BookPageFlip(
    pageCount: Int,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (pageIndex: Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Align current page for dual spread in landscape
    val leftPage = if (isLandscape) (currentPage / 2) * 2 else currentPage

    // Drag and flip progress (-1f to 1f)
    // 0f = idle
    // > 0f = turning to next page
    // < 0f = turning to previous page
    val flipProgress = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLandscape, leftPage, pageCount) {
                var totalDrag = 0f
                val screenWidth = size.width.toFloat()
                
                detectDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount.x
                        
                        // Dragging left (next page) -> positive progress
                        // Dragging right (prev page) -> negative progress
                        val progressDelta = -dragAmount.x / screenWidth
                        
                        coroutineScope.launch {
                            val newProgress = (flipProgress.value + progressDelta).coerceIn(-1f, 1f)
                            
                            // Check bounds
                            val canGoNext = if (isLandscape) {
                                leftPage + 2 < pageCount
                            } else {
                                leftPage + 1 < pageCount
                            }
                            val canGoPrev = leftPage > 0
                            
                            if (newProgress > 0f && !canGoNext) {
                                flipProgress.snapTo(0f)
                            } else if (newProgress < 0f && !canGoPrev) {
                                flipProgress.snapTo(0f)
                            } else {
                                flipProgress.snapTo(newProgress)
                            }
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            val target = when {
                                flipProgress.value > 0.35f -> 1f
                                flipProgress.value < -0.35f -> -1f
                                else -> 0f
                            }
                            
                            flipProgress.animateTo(
                                targetValue = target,
                                animationSpec = tween(durationMillis = 350)
                            )
                            
                            if (target == 1f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isLandscape) {
                                    onPageChanged((leftPage + 2).coerceAtMost(pageCount - 1))
                                } else {
                                    onPageChanged((leftPage + 1).coerceAtMost(pageCount - 1))
                                }
                            } else if (target == -1f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isLandscape) {
                                    onPageChanged((leftPage - 2).coerceAtLeast(0))
                                } else {
                                    onPageChanged((leftPage - 1).coerceAtLeast(0))
                                }
                            }
                            flipProgress.snapTo(0f)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            flipProgress.animateTo(0f, tween(durationMillis = 200))
                        }
                    }
                )
            }
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val progress = flipProgress.value

        if (isLandscape) {
            // LANDSCAPE: Two-page spread
            val halfWidthDp = with(density) { (width / 2).toDp() }
            val heightDp = with(density) { height.toDp() }

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE5E2DA))) {
                // Background shadow under center spine
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(24.dp)
                        .align(Alignment.Center)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.15f)
                                )
                            )
                        )
                )

                // 1. Static underlying pages (Bottom Layer)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left page underneath
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val underLeftIndex = if (progress < 0f) leftPage - 2 else leftPage
                        if (underLeftIndex in 0 until pageCount) {
                            PageContainer(halfWidthDp, heightDp) {
                                pageContent(underLeftIndex)
                            }
                        }
                    }
                    // Right page underneath
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val underRightIndex = if (progress > 0f) leftPage + 3 else leftPage + 1
                        if (underRightIndex in 0 until pageCount) {
                            PageContainer(halfWidthDp, heightDp) {
                                pageContent(underRightIndex)
                            }
                        }
                    }
                }

                // 2. Shadows cast by turning page
                if (progress != 0f) {
                    val shadowAlpha = abs(sin(progress.toDouble() * kotlin.math.PI / 2.0)).toFloat() * 0.25f
                    if (progress > 0f) {
                        // Shadow cast on the right page from the spine
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(halfWidthDp)
                                .align(Alignment.CenterEnd)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = shadowAlpha),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    } else {
                        // Shadow cast on the left page from the spine
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(halfWidthDp)
                                .align(Alignment.CenterStart)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = shadowAlpha)
                                        )
                                    )
                                )
                        )
                    }
                }

                // 3. Turning Page (Top Layer)
                if (progress != 0f) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (progress > 0f) {
                            // Turning page is on the right, flipping to left
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        val angle = -progress * 180f
                                        rotationY = angle
                                        cameraDistance = 15f * density.density
                                        transformOrigin = TransformOrigin(0f, 0.5f) // Pivot at spine
                                    }
                                    .clipToBounds()
                            ) {
                                val angle = -progress * 180f
                                val showFront = angle >= -90f
                                val turnPageIndex = if (showFront) leftPage + 1 else leftPage + 2

                                if (turnPageIndex in 0 until pageCount) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                // If showing the back of the turning page, mirror it to un-reverse text
                                                if (!showFront) {
                                                    scaleX = -1f
                                                }
                                            }
                                    ) {
                                        PageContainer(halfWidthDp, heightDp) {
                                            pageContent(turnPageIndex)
                                        }
                                        
                                        // Shading overlay
                                        val shadeAlpha = (1.0 - cos(progress.toDouble() * kotlin.math.PI / 2.0)).toFloat() * 0.3f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = shadeAlpha))
                                        )
                                    }
                                }
                            }
                        } else {
                            // Turning page is on the left, flipping to right
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        val angle = -180f * (1f + progress)
                                        rotationY = angle
                                        cameraDistance = 15f * density.density
                                        transformOrigin = TransformOrigin(1f, 0.5f) // Pivot at spine
                                    }
                                    .clipToBounds()
                            ) {
                                val angle = -180f * (1f + progress)
                                val showFront = angle >= -90f
                                val turnPageIndex = if (showFront) leftPage - 1 else leftPage - 2

                                if (turnPageIndex in 0 until pageCount) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                // Mirror the back content
                                                if (!showFront) {
                                                    scaleX = -1f
                                                }
                                            }
                                    ) {
                                        PageContainer(halfWidthDp, heightDp) {
                                            pageContent(turnPageIndex)
                                        }

                                        // Shading overlay
                                        val shadeAlpha = (1.0 - cos((1.0 + progress.toDouble()) * kotlin.math.PI / 2.0)).toFloat() * 0.3f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = shadeAlpha))
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Decorative Center Binding/Fold Ring Effect
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }

        } else {
            // PORTRAIT: Single-page book view
            val widthDp = with(density) { width.toDp() }
            val heightDp = with(density) { height.toDp() }

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEFECE5))) {
                
                // 1. Static underlying page (Bottom Layer)
                Box(modifier = Modifier.fillMaxSize()) {
                    val underIndex = if (progress >= 0f) leftPage + 1 else leftPage
                    if (underIndex in 0 until pageCount) {
                        PageContainer(widthDp, heightDp) {
                            pageContent(underIndex)
                        }
                    }
                }

                // 2. Page Curl Shadow cast on underlying page
                if (progress != 0f) {
                    val shadowAlpha = abs(sin(progress.toDouble() * kotlin.math.PI / 2.0)).toFloat() * 0.3f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(widthDp * 0.3f)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = shadowAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // 3. Turning Page (Top Layer)
                Box(modifier = Modifier.fillMaxSize()) {
                    val angle = if (progress >= 0f) {
                        -progress * 180f
                    } else {
                        -180f * (1f + progress)
                    }

                    // Only show turning page when it is in the visible forward range [-90, 0] degrees
                    // In portrait, rotation past -90 degrees flips it off-screen to the left (negative x space)
                    if (angle >= -90f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationY = angle
                                    cameraDistance = 15f * density.density
                                    transformOrigin = TransformOrigin(0f, 0.5f) // Pivot at left edge
                                }
                                .clipToBounds()
                        ) {
                            val turnPageIndex = if (progress >= 0f) leftPage else leftPage - 1
                            if (turnPageIndex in 0 until pageCount) {
                                PageContainer(widthDp, heightDp) {
                                    pageContent(turnPageIndex)
                                }

                                // 3D Tilt Shading Overlay
                                val relativeProgress = if (progress >= 0f) progress else (1f + progress)
                                val shadeAlpha = (1.0 - cos(relativeProgress.toDouble() * kotlin.math.PI / 2.0)).toFloat() * 0.4f
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = shadeAlpha))
                                )
                            }
                        }
                    }
                }

                // Left Edge Hinge Shadow (Spine look-alike)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun PageContainer(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 650.dp)
                .fillMaxHeight()
                .background(Color.White)
                .graphicsLayer {
                    // Soft paper shadow
                    shadowElevation = 8f
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    clip = true
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
