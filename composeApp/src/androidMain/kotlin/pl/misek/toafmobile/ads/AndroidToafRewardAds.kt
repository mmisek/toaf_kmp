package pl.misek.toafmobile.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import pl.misek.toafmobile.tools.isDebug

class AndroidToafRewardAds(private val context: Context) : IToafRewardAds
{
    private val tag = "ToafRewardAds"
    private var rewardedAd : RewardedAd? = null

    private fun createRewardedAd() : AdRequest {
        return AdRequest.Builder().build()
    }

    override fun isAdLoaded() = rewardedAd != null

    override fun loadAd(onAdLoaded : () -> Unit, onAdFailed : () -> Unit)
    {
        rewardedAd = null
        RewardedAd.load(
            context,
            if (isDebug()) REWARDED_AD_TEST_ID else REWARDED_AD_ID,
            createRewardedAd(),
            object : RewardedAdLoadCallback()
            {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d(tag, "onAdFailedToLoad $loadAdError")
                    loadAd({},{})
                    onAdFailed()
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(tag, "Ad was loaded.")
                    rewardedAd = ad
                    onAdLoaded()
                }
            }
        )
    }

    override fun showAd(activity: Any?, onRewardEarned: (Int) -> Unit, onAdClosed: () ->Unit)
    {
        val act = activity as? Activity
        if (rewardedAd !=null && act != null)
        {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback()
            {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(tag, "Ad was loaded.")
                    rewardedAd = null
                    loadAd({},{})
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    Log.d(tag, "Ad failed to show full screen content")
                    rewardedAd = null
                    onAdClosed()
                }
            }

            rewardedAd?.show(act) { rewardItem ->
                val rewarderAmount = rewardItem.amount
                val rewarderType = rewardItem.type
                Log.d(tag, "User earned item : $rewarderAmount $rewarderType")
                onRewardEarned(rewarderAmount)
            }
        }
        else
        {
            Log.d(tag, "Ad is not ready")
            loadAd({},{})
        }
    }

    init {
        loadAd({},{})
    }

    companion object {
        const val AD_ID: String = "ca-app-pub-7901370159480300/6283371199"
        const val AD_TEST_ID: String = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_DEVICE_GALAXY_S6: String = "B431EE858B5F1986E4D89CA31250F732"
        const val TEST_DEVICE_XIAOMI_REDMI_9C_NFC: String = "769E54F5CA86C265D3C753C7E0F845B2"
        const val TEST_DEVICE_ANDROID_X86: String = "89A6E1578CC951BA188CE016B7AA76DE"
        private const val REWARDED_AD_TEST_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val REWARDED_AD_ID = "ca-app-pub-7901370159480300/6309837648"
    }
}
