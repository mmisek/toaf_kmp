package pl.misek.toafmobile.activities.composeModels.menus

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

@Composable
expect fun ToafVerticalScrollbar(gridState : LazyGridState)

@Composable
expect fun ToafVerticalScrollbar(listState: LazyListState)

@Composable
expect fun ToafVerticalScrollbar(scrollState: ScrollState)
