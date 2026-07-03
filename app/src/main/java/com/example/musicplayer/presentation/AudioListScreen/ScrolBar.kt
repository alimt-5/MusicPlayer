package com.example.musicplayer.presentation.AudioListScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun ScrollBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }

    LaunchedEffect(progress) {
        if (!isDragging) dragProgress = progress
    }

    val displayProgress = if (isDragging) dragProgress else progress

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.y / size.height).coerceIn(0f, 1f)
                    dragProgress = newProgress
                    onDrag(newProgress)
                }
            }

    ) {
        val thumbSize = 22.dp
        val maxOffset = (maxHeight - thumbSize).coerceAtLeast(0.dp)
        val thumbOffset = maxOffset * displayProgress

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        val progress = (change.position.y / size.height).coerceIn(0f, 1f)
                        dragProgress = progress
                        onDrag(progress)
                    }
                }
        )

        Box(
            modifier = Modifier
                .offset(y = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp)
                )
        )
    }
}