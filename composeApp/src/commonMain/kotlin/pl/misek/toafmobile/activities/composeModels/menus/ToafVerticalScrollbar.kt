package pl.misek.toafmobile.activities.composeModels.menus

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

@Composable
expect fun ToafVerticalScrollbar(gridState : LazyGridState)
