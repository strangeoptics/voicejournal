package com.example.voicejournal.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    val targetAlpha by animateFloatAsState(
        targetValue = if (isVisible || listState.isScrollInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = if (isVisible) 150 else 500)
    )

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isVisible = true
        } else {
            kotlinx.coroutines.delay(1000)
            isVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
            .alpha(targetAlpha)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isVisible = true },
                    onDragEnd = { isVisible = false }
                ) { change, _ ->
                    coroutineScope.launch {
                        val totalItems = listState.layoutInfo.totalItemsCount
                        if (totalItems > 0) {
                            val targetItem =
                                (change.position.y / size.height * totalItems).toInt()
                            listState.scrollToItem(targetItem.coerceIn(0, totalItems - 1))
                        }
                    }
                    change.consume()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .width(8.dp)
                .fillMaxHeight(0.5f) // Just an indicator, not a draggable thumb
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
        )
    }
}
