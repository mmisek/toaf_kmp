package pl.misek.toafmobile.activities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.splash_logo
import kotlin.time.Duration.Companion.milliseconds

private val LOGO_HORIZONTAL_PADDING = 24.dp
// rozmiar logo przyjmowany, gdy nie uda się odczytać ikony systemowego splash screena
private val FALLBACK_START_LOGO_SIZE = 192.dp
private const val SYSTEM_SPLASH_WAIT_MS = 1500L
private const val LOGO_GROW_MS = 500
private const val LOGO_HOLD_MS = 700L
private const val SPLASH_FADE_OUT_MS = 400

/**
 * Kontynuacja systemowego splash screena, którego ikona ma stały, mały rozmiar:
 * logo startuje z miejsca i rozmiaru ikony systemowej ([systemLogoBounds], w pikselach okna),
 * powiększa się do pełnej szerokości ekranu, a następnie cały splash zanika odsłaniając aplikację.
 */
@Composable
fun SplashOverlay(systemLogoBounds: Rect?, onFinished: () -> Unit) {
    val currentSystemLogoBounds by rememberUpdatedState(systemLogoBounds)
    val progress = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    var startBounds by remember { mutableStateOf<Rect?>(null) }
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(ToafColors.toafBackground)
            .onGloballyPositioned { containerBounds = it.boundsInWindow() }
            // blokuje dotyk do aplikacji pod spodem, dopóki splash jest widoczny
            .clickable(interactionSource = null, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        val fullLogoSize = min(maxWidth - LOGO_HORIZONTAL_PADDING * 2, maxHeight)
        val fullLogoSizePx = with(density) { fullLogoSize.toPx() }

        LaunchedEffect(Unit) {
            val container = snapshotFlow { containerBounds }.first { it != null }!!
            startBounds = withTimeoutOrNull(SYSTEM_SPLASH_WAIT_MS.milliseconds) {
                snapshotFlow { currentSystemLogoBounds }.first { it != null }
            } ?: Rect(
                center = container.center,
                radius = with(density) { FALLBACK_START_LOGO_SIZE.toPx() } / 2f
            )
            progress.animateTo(1f, tween(LOGO_GROW_MS, easing = FastOutSlowInEasing))
            delay(LOGO_HOLD_MS.milliseconds)
            overlayAlpha.animateTo(0f, tween(SPLASH_FADE_OUT_MS, easing = LinearOutSlowInEasing))
            onFinished()
        }

        Box(
            modifier = Modifier
                .size(fullLogoSize)
                .graphicsLayer {
                    val start = startBounds
                    val container = containerBounds
                    if (start == null || container == null) {
                        // systemowy splash jest jeszcze na wierzchu
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val p = progress.value
                    val scale = lerp(start.width / fullLogoSizePx, 1f, p)
                    val startOffset: Offset = start.center - container.center
                    scaleX = scale
                    scaleY = scale
                    translationX = lerp(startOffset.x, 0f, p)
                    translationY = lerp(startOffset.y, 0f, p)
                }
        ) {
            Image(
                painter = painterResource(Res.drawable.splash_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
