package pl.misek.toafmobile.activities.composeModels.menus

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import pl.misek.toafmobile.activities.definitions.ToafColors

@Composable
actual fun ToafVerticalScrollbar(gridState : LazyGridState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(10.dp)
            .padding(horizontal = 2.dp)
            .drawBehind {
                val layoutInfo = gridState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@drawBehind
                if (!gridState.canScrollForward && !gridState.canScrollBackward) return@drawBehind

                // liczba kolumn = elementy w pierwszym widocznym wierszu
                val firstRow = visibleItems.first().row
                val columns = visibleItems.count { it.row == firstRow }.coerceAtLeast(1)
                val totalRows = (layoutInfo.totalItemsCount + columns - 1) / columns
                val rowSize = visibleItems.sumOf { it.size.height } / visibleItems.size.toFloat() +
                        layoutInfo.mainAxisItemSpacing

                val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                val contentHeight = rowSize * totalRows
                val scrollOffset = rowSize * (gridState.firstVisibleItemIndex / columns) +
                        gridState.firstVisibleItemScrollOffset

                drawToafScrollbar(viewportHeight, contentHeight, scrollOffset, !gridState.canScrollForward)
            }
    )
}

@Composable
actual fun ToafVerticalScrollbar(listState: LazyListState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(10.dp)
            .padding(horizontal = 2.dp)
            .drawBehind {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@drawBehind
                if (!listState.canScrollForward && !listState.canScrollBackward) return@drawBehind

                // elementy mają różne wysokości - przyjmujemy średnią z widocznych
                val itemSize = visibleItems.sumOf { it.size } / visibleItems.size.toFloat() +
                        layoutInfo.mainAxisItemSpacing

                val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                val contentHeight = itemSize * layoutInfo.totalItemsCount
                val scrollOffset = itemSize * listState.firstVisibleItemIndex +
                        listState.firstVisibleItemScrollOffset

                drawToafScrollbar(viewportHeight, contentHeight, scrollOffset, !listState.canScrollForward)
            }
    )
}

@Composable
actual fun ToafVerticalScrollbar(scrollState: ScrollState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(10.dp)
            .padding(horizontal = 2.dp)
            .drawBehind {
                if (scrollState.maxValue <= 0) return@drawBehind
                val viewportHeight = scrollState.viewportSize.toFloat()
                drawToafScrollbar(
                    viewportHeight,
                    viewportHeight + scrollState.maxValue,
                    scrollState.value.toFloat(),
                    scrollState.value >= scrollState.maxValue
                )
            }
    )
}

private fun DrawScope.drawToafScrollbar(viewportHeight: Float, estimatedContentHeight: Float, scrollOffset: Float, isAtEnd: Boolean) {
    // wysokość treści jest szacowana - może wyjść mniejsza niż widok, mimo że lista się przewija
    val contentHeight = estimatedContentHeight.coerceAtLeast(viewportHeight + 1f)

    val trackHeight = size.height
    val thickness = size.width
    val maxScroll = contentHeight - viewportHeight
    val thumbHeight = (trackHeight * viewportHeight / contentHeight)
        .coerceIn(24.dp.toPx().coerceAtMost(trackHeight), trackHeight)
    val thumbTop = if (isAtEnd) trackHeight - thumbHeight
    else ((trackHeight - thumbHeight) * (scrollOffset / maxScroll)).coerceIn(0f, trackHeight - thumbHeight)

    drawRoundRect(
        color = ToafColors.toafTextColorWhite.copy(alpha = 0.3f),
        topLeft = Offset(0f, 0f),
        size = Size(thickness, trackHeight),
        cornerRadius = CornerRadius(thickness / 2)
    )
    drawRoundRect(
        color = ToafColors.toafTextColorWhite.copy(alpha = 0.9f),
        topLeft = Offset(0f, thumbTop),
        size = Size(thickness, thumbHeight),
        cornerRadius = CornerRadius(thickness / 2)
    )
}
