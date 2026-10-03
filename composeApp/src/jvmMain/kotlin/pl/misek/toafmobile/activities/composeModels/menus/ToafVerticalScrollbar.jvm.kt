package pl.misek.toafmobile.activities.composeModels.menus

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.misek.toafmobile.activities.definitions.ToafColors

@Composable
actual fun ToafVerticalScrollbar(gridState : LazyGridState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(16.dp)
            .background(Color.Gray.copy(alpha = 0.1f))
    ) {
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.Center),
            adapter = rememberScrollbarAdapter(scrollState = gridState),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 12.dp,
                shape = RoundedCornerShape(10.dp),
                hoverDurationMillis = 0,
                unhoverColor = ToafColors.toafTextColorWhite.copy(alpha = 0.5f),
                hoverColor = ToafColors.toafTextColorWhite.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
actual fun ToafVerticalScrollbar(listState: LazyListState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(16.dp)
            .background(Color.Gray.copy(alpha = 0.1f))
    ) {
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.Center),
            adapter = rememberScrollbarAdapter(scrollState = listState),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 12.dp,
                shape = RoundedCornerShape(10.dp),
                hoverDurationMillis = 0,
                unhoverColor = ToafColors.toafTextColorWhite.copy(alpha = 0.5f),
                hoverColor = ToafColors.toafTextColorWhite.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
actual fun ToafVerticalScrollbar(scrollState: ScrollState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(16.dp)
            .background(Color.Gray.copy(alpha = 0.1f))
    ) {
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.Center),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 12.dp,
                shape = RoundedCornerShape(10.dp),
                hoverDurationMillis = 0,
                unhoverColor = ToafColors.toafTextColorWhite.copy(alpha = 0.5f),
                hoverColor = ToafColors.toafTextColorWhite.copy(alpha = 0.8f)
            )
        )
    }
}
