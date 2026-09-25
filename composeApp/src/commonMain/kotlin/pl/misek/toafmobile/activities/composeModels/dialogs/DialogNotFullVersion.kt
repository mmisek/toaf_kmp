package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.ads.ToafRewardAds
import pl.misek.toafmobile.billing.IToafBilling
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.tools.getPlatformActivity
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogNotFullVersion(onWatchAdResult: (Boolean) -> Unit, onCloseClick : () ->Unit)
{
    val activity = getPlatformActivity()
    val toafBilling : IToafBilling = koinInject()
    val adHelper : ToafRewardAds = koinInject()
    val toafApi : ToafApi = koinInject()
    var fullVersionPrice by remember { mutableStateOf<String?>(null) }
    var isPriceLoading by remember { mutableStateOf(true) }
    var isUserLoggedIn by remember { mutableStateOf(false) }
    var adReady by remember { mutableStateOf(false) }
    val fullVersionPriceText = stringResource(Res.string.dialog_full_version_get_full_version)
    LaunchedEffect(Unit) {
        if (getPlatformType() == PlatformType.Android) {
            adReady = adHelper.isAdLoaded()
            if (!adReady) {
                adHelper.loadAd(
                    onAdLoaded = { adReady = true },
                    onAdFailed = { adReady = false }
                )
            }
        }
        isUserLoggedIn = toafApi.isCredentialValid()
        fullVersionPrice = toafBilling.getElementPrice(ToafConstants.SKU_TOAF_FULL_VERSION)
        isPriceLoading = false
    }
    AlertDialog(
        shape = toafShape,
        modifier = Modifier.border(
            width = 1.dp,
            shape = toafShape,
            color = ToafColors.toafTextColorWhite
        ),

        containerColor = ToafColors.toafBackground,
        onDismissRequest = { onCloseClick() },
        title = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp))
            {
                Text(
                    text = stringResource(Res.string.activity_main_function_not_supported_in_this_version),
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        },
        confirmButton = {
            Column {
                if (getPlatformType() == PlatformType.Android) {
                    ToafButtons.GreenButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        buttonText = Res.string.dialog_full_version_watch_ad,
                        onClick = {
                            adHelper.showAd(
                                activity = activity,
                                onRewardEarned = { onWatchAdResult(true) },
                                onAdClosed = {
                                    adReady = false
                                    onWatchAdResult(false)
                                })
                        },
                        enabled = adReady
                    )
                    Text(
                        text = stringResource(Res.string.dialog_full_version_or),
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
                if (isPriceLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(40.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                else if (fullVersionPrice != null) {
                    ToafButtons.GreenButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        buttonText = String.format(
                            fullVersionPriceText,
                            fullVersionPrice ?: "??"
                        ),
                        enabled = isUserLoggedIn,
                        onClick = {
                            activity?.let {
                                toafBilling.buyElement(
                                    activity = it,
                                    productName = ToafConstants.SKU_TOAF_FULL_VERSION
                                )
                            }
                            onCloseClick()
                        })
                    if (!isUserLoggedIn) {
                        Text(
                            text = stringResource(Res.string.dialog_full_version_login_required),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp),
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                    }
                }
                else {
                    Text(
                        text = stringResource(
                            if (isUserLoggedIn) Res.string.dialog_full_version_price_error
                            else Res.string.dialog_full_version_login_required_for_price
                        ),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
                ToafButtons.RedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.dialog_full_version_cancel_dialog,
                    onClick = { onCloseClick() })
            }
        }
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogNotFullVersionPreview()
{
    DialogNotFullVersion(
        onWatchAdResult = {},
        onCloseClick = {})
}