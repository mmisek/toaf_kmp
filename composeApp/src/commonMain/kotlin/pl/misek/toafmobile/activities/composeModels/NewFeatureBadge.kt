package pl.misek.toafmobile.activities.composeModels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.new_feature_badge

private val newFeatureColor = Color(0xFFD32F2F)

/** Ukosna wstega NEW w prawym gornym rogu - dla kafelkow (przycisk o ksztalcie [shape] wypelnia caly Box) */
@Composable
fun BoxScope.NewFeatureRibbon(shape: Shape, ribbonSize: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
    )
    {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(ribbonSize)
        )
        {
            // Srodek wstegi przesuniety po przekatnej w strone rogu
            Text(
                text = stringResource(Res.string.new_feature_badge),
                color = Color.White,
                fontSize = (ribbonSize.value * 0.17f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = ribbonSize * 0.16f, y = -ribbonSize * 0.16f)
                    .rotate(45f)
                    .requiredWidth(ribbonSize * 2)
                    .background(newFeatureColor)
            )
        }
    }
}

/** Plakietka NEW na gornej krawedzi przycisku, przy prawym rogu - dla przyciskow zaokraglonych */
@Composable
fun BoxScope.NewFeatureChip() {
    Text(
        text = stringResource(Res.string.new_feature_badge),
        color = Color.White,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.TopEnd)
            // Rodzic moze wymuszac min. rozmiar (propagateMinConstraints) - plakietka zawsze ma wlasny rozmiar
            .wrapContentSize(Alignment.TopEnd)
            .padding(end = 20.dp)
            .background(newFeatureColor, RoundedCornerShape(50))
            .border(1.dp, Color.White, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun NewFeatureBadgePreview()
{
    Box(Modifier.padding(5.dp))
    {
        ToafButtons.GreenButton(
            buttonText = Res.string.new_feature_badge,
            onClick = {},
            modifier = Modifier,
            isNew = true
        )
    }
}
