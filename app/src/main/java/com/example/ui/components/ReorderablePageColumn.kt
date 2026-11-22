package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Coordinates drag-and-drop reordering for items inside a [LazyListState].
 * Supports immediate dragging via [ReorderableDragHandle] as well as long-press drag
 * anywhere on an item card, with edge auto-scrolling and haptic feedback.
 */
@Stable
class ReorderableLazyListState internal constructor(
    val lazyListState: LazyListState,
    private val hapticFeedback: HapticFeedback,
    private val edgeScrollThresholdPx: Float,
    private val maxAutoScrollStepPx: Float,
    private val enabledProvider: () -> Boolean,
    private val onMoveProvider: () -> ((fromIndex: Int, toIndex: Int) -> Unit),
    private val onDragEndProvider: () -> (() -> Unit)
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialDraggedOffset by mutableIntStateOf(0)
    private var initialDraggedSize by mutableIntStateOf(0)

    private var listCoordinates: LayoutCoordinates? = null
    private val handleCoordsByKey = mutableMapOf<Any, LayoutCoordinates>()

    val isEnabled: Boolean
        get() = enabledProvider()

    internal fun updateListCoordinates(coordinates: LayoutCoordinates) {
        listCoordinates = coordinates
    }

    internal fun registerDragHandle(key: Any, coordinates: LayoutCoordinates) {
        handleCoordsByKey[key] = coordinates
    }

    internal fun unregisterDragHandle(key: Any) {
        handleCoordsByKey.remove(key)
    }

    internal fun findDragHandleItem(positionInList: Offset): LazyListItemInfo? {
        val container = listCoordinates?.takeIf { it.isAttached } ?: return null
        for (item in lazyListState.layoutInfo.visibleItemsInfo) {
            val handle = handleCoordsByKey[item.key]?.takeIf { it.isAttached } ?: continue
            val bounds = container.localBoundingBoxOf(handle, clipBounds = false)
            if (bounds.contains(positionInList)) {
                return item
            }
        }
        return null
    }

    internal fun findItemAt(positionInList: Offset): LazyListItemInfo? {
        val contentY = (positionInList.y - lazyListState.layoutInfo.beforeContentPadding).roundToInt()
        return lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            contentY in item.offset..(item.offset + item.size)
        }
    }

    internal fun onDragStart(item: LazyListItemInfo) {
        draggingItemKey = item.key
        draggingItemIndex = item.index
        initialDraggedOffset = item.offset
        initialDraggedSize = item.size
        draggedDistance = 0f
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun onDrag(delta: Offset) {
        if (draggingItemKey == null) return
        draggedDistance += delta.y
        checkForSwap()
    }

    internal fun onDragEnd() {
        val wasDragging = draggingItemKey != null
        draggingItemKey = null
        draggingItemIndex = null
        draggedDistance = 0f
        initialDraggedOffset = 0
        initialDraggedSize = 0
        if (wasDragging) {
            onDragEndProvider().invoke()
        }
    }

    internal fun checkForSwap() {
        val key = draggingItemKey ?: return
        val trackedIndex = draggingItemIndex ?: return
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentElement = visibleItems.firstOrNull { it.key == key } ?: return

        // Wait for Compose layout pass to settle after the previous swap
        if (currentElement.index != trackedIndex) return

        val startOffset = initialDraggedOffset + draggedDistance
        val endOffset = startOffset + currentElement.size

        val nextItem = visibleItems.firstOrNull { it.index == trackedIndex + 1 }
        if (nextItem != null && endOffset > nextItem.offset + nextItem.size * 0.5f) {
            draggingItemIndex = nextItem.index
            onMoveProvider().invoke(trackedIndex, nextItem.index)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }

        val prevItem = visibleItems.firstOrNull { it.index == trackedIndex - 1 }
        if (prevItem != null && startOffset < prevItem.offset + prevItem.size * 0.5f) {
            draggingItemIndex = prevItem.index
            onMoveProvider().invoke(trackedIndex, prevItem.index)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    internal fun computeAutoScrollStep(): Float {
        if (draggingItemKey == null) return 0f
        val layoutInfo = lazyListState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val draggedTop = initialDraggedOffset + draggedDistance
        val draggedBottom = draggedTop + initialDraggedSize

        return when {
            draggedBottom > viewportEnd - edgeScrollThresholdPx && lazyListState.canScrollForward -> {
                val ratio = ((draggedBottom - (viewportEnd - edgeScrollThresholdPx)) / edgeScrollThresholdPx)
                    .coerceIn(0f, 1f)
                ratio * maxAutoScrollStepPx
            }

            draggedTop < viewportStart + edgeScrollThresholdPx && lazyListState.canScrollBackward -> {
                val ratio = (((viewportStart + edgeScrollThresholdPx) - draggedTop) / edgeScrollThresholdPx)
                    .coerceIn(0f, 1f)
                -ratio * maxAutoScrollStepPx
            }

            else -> 0f
        }
    }

    internal fun currentItemTranslationY(key: Any): Float {
        if (key != draggingItemKey) return 0f
        val currentElement = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            ?: return 0f
        return (initialDraggedOffset + draggedDistance) - currentElement.offset
    }
}

@Composable
fun rememberReorderableLazyListState(
    lazyListState: LazyListState = rememberLazyListState(),
    enabled: Boolean = true,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit = {}
): ReorderableLazyListState {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val edgeThresholdPx = with(density) { 72.dp.toPx() }
    val maxScrollStepPx = with(density) { 18.dp.toPx() }

    val state = remember(lazyListState, haptic, edgeThresholdPx, maxScrollStepPx) {
        ReorderableLazyListState(
            lazyListState = lazyListState,
            hapticFeedback = haptic,
            edgeScrollThresholdPx = edgeThresholdPx,
            maxAutoScrollStepPx = maxScrollStepPx,
            enabledProvider = { currentEnabled },
            onMoveProvider = { currentOnMove },
            onDragEndProvider = { currentOnDragEnd }
        )
    }

    LaunchedEffect(state, state.draggingItemKey) {
        if (state.draggingItemKey == null) return@LaunchedEffect
        while (state.draggingItemKey != null) {
            withFrameNanos { }
            val step = state.computeAutoScrollStep()
            if (step != 0f) {
                lazyListState.scrollBy(step)
                state.checkForSwap()
            }
        }
    }

    return state
}

/**
 * Attaches drag-and-drop pointer gesture handling and coordinate tracking to a [LazyColumn].
 * Uses [PointerEventPass.Initial] on the non-translating list container so item `graphicsLayer`
 * translations never distort pointer coordinates, while normal taps and scrolls pass through
 * unaffected unless a drag handle or long-press activates reordering.
 */
fun Modifier.reorderableContainer(state: ReorderableLazyListState): Modifier = this
    .onGloballyPositioned { state.updateListCoordinates(it) }
    .pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!state.isEnabled) return@awaitEachGesture

            val handleItem = state.findDragHandleItem(down.position)
            var startedKey: Any? = null

            if (handleItem != null) {
                var pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()
                    if (change == null || !change.pressed) break
                    pointerId = change.id
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        val delta = change.position - down.position
                        change.consume()
                        startedKey = handleItem.key
                        state.onDragStart(handleItem)
                        state.onDrag(delta)
                        break
                    }
                }
            } else {
                val cancelledBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) return@withTimeoutOrNull true
                        pointerId = change.id
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            return@withTimeoutOrNull true
                        }
                    }
                }
                if (cancelledBeforeLongPress == null) {
                    val targetItem = state.findItemAt(down.position)
                    if (targetItem != null) {
                        startedKey = targetItem.key
                        state.onDragStart(targetItem)
                    }
                }
            }

            if (startedKey != null) {
                var pointerId = down.id
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            change?.consume()
                            break
                        }
                        pointerId = change.id
                        val delta = change.positionChange()
                        change.consume()
                        state.onDrag(delta)
                    }
                } finally {
                    state.onDragEnd()
                }
            }
        }
    }

/**
 * Registers a drag handle region inside an item so dragging from it starts reordering immediately
 * without waiting for a long press.
 */
@Composable
fun ReorderableDragHandle(
    state: ReorderableLazyListState,
    itemKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    DisposableEffect(state, itemKey) {
        onDispose { state.unregisterDragHandle(itemKey) }
    }
    Box(
        modifier = modifier.onGloballyPositioned { state.registerDragHandle(itemKey, it) },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Wraps an item inside a reorderable [LazyColumn], applying smooth sibling placement animations
 * and elevated visual lift while dragging.
 */
@Composable
fun LazyItemScope.ReorderablePageItem(
    state: ReorderableLazyListState,
    itemKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = state.draggingItemKey == itemKey
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reorder_item_scale"
    )

    val itemModifier = modifier
        .zIndex(if (isDragging) 10f else 0f)
        .then(
            if (!isDragging) {
                Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset.VisibilityThreshold
                    )
                )
            } else {
                Modifier
            }
        )
        .graphicsLayer {
            translationY = if (isDragging) state.currentItemTranslationY(itemKey) else 0f
            scaleX = scale
            scaleY = scale
        }

    Box(modifier = itemModifier) {
        content(isDragging)
    }
}
