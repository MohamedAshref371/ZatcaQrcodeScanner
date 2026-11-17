package com.poetscorner.invoiceqr.ui

import com.poetscorner.invoiceqr.resources.Res
import com.poetscorner.invoiceqr.resources.ic_arrow_up
import com.poetscorner.invoiceqr.resources.scroll_to_top
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.painterResource
import com.poetscorner.invoiceqr.ui.text.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ScrollToTopContainer(listState: LazyListState, content: @Composable () -> Unit) {
    var distanceFromTop by rememberSaveable { mutableFloatStateOf(0f) }
    val scrollConnection = remember { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset,
            source: NestedScrollSource): Offset {
            distanceFromTop = (distanceFromTop - consumed.y).coerceAtLeast(0f)
            return Offset.Zero
        }
    } }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .collect { atTop -> if (atTop) distanceFromTop = 0f }
    }
    val showButton by remember(listState) { derivedStateOf {
        val layout = listState.layoutInfo
        val viewportHeight = layout.viewportSize.height
        val visibleItems = layout.visibleItemsInfo
        val averageItemHeight = if (visibleItems.isEmpty()) 0f
            else visibleItems.sumOf { it.size }.toFloat() / visibleItems.size + layout.mainAxisItemSpacing
        val estimatedDistance = listState.firstVisibleItemIndex * averageItemHeight +
            listState.firstVisibleItemScrollOffset
        viewportHeight > 0 && maxOf(distanceFromTop, estimatedDistance) > viewportHeight * 2f
    } }
    val scope = rememberCoroutineScope()
    val label = stringResource(Res.scroll_to_top)
    Box(Modifier.fillMaxSize().nestedScroll(scrollConnection)) {
        content()
        AnimatedVisibility(visible = showButton, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)) {
            IconButton(onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.size(48.dp).testTag("scroll_to_top").semantics {
                    contentDescription = label
                }) {
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 3.dp) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(Res.ic_arrow_up), contentDescription = null,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
