package pl.misek.toafmobile.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import co.touchlab.kermit.Logger
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.tools.isDebug

private const val AD_ID: String = "ca-app-pub-7901370159480300/6283371199"
private const val AD_TEST_ID: String = "ca-app-pub-3940256099942544/6300978111"
private const val TEST_DEVICE_GALAXY_S6: String = "B431EE858B5F1986E4D89CA31250F732"
private const val TEST_DEVICE_XIAOMI_REDMI_9C_NFC: String = "769E54F5CA86C265D3C753C7E0F845B2"
private const val TEST_DEVICE_ANDROID_X86: String = "89A6E1578CC951BA188CE016B7AA76DE"

@Composable
actual fun AdMobBanner(modifier: Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {context ->
            AdView(context).apply {
                setAdSize(AdSize.FULL_BANNER)
                adUnitId = if (isDebug()) AD_TEST_ID else AD_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        update  = { adView ->
            Logger.i { "Ad Loaded , ${adView.id}"}
        }
    )
}
