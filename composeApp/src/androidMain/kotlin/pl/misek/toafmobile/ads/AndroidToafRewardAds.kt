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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.misek.toafmobile.tools.isDebug

class AndroidToafRewardAds(private val context: Context) : ToafRewardAds
{
    private val tag = "ToafRewardAds"
    private var rewardedAd : RewardedAd? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retryJob : Job? = null
    private var retryCount = 0

    private val pendingCallbacks = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    @Volatile
    private var isLoading = false

    private fun createRewardedAd() : AdRequest {
        return AdRequest.Builder().build()
    }

    override fun isAdLoaded() = rewardedAd != null

    /** Wejscie uzytkownika - kasuje licznik ponowien, zeby backoff nie blokowal recznej proby. */
    override fun loadAd(onAdLoaded : () -> Unit, onAdFailed : () -> Unit)
    {
        retryCount = 0
        startLoad(onAdLoaded, onAdFailed)
    }

    private fun startLoad(onAdLoaded : () -> Unit, onAdFailed : () -> Unit)
    {
        synchronized(pendingCallbacks) {
            pendingCallbacks.add(onAdLoaded to onAdFailed)
            if (isLoading) {
                Log.d(tag, "load already in progress - waiting for its result")
                return
            }
            isLoading = true
        }
        retryJob?.cancel()
        retryJob = null
        rewardedAd = null
        RewardedAd.load(
            context,
            if (isDebug()) REWARDED_AD_TEST_ID else REWARDED_AD_ID,
            createRewardedAd(),
            object : RewardedAdLoadCallback()
            {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoading = false
                    Log.d(tag, "onAdFailedToLoad $loadAdError")
                    scheduleRetry()
                    notifyPending(loaded = false)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    retryCount = 0
                    Log.d(tag, "Ad was loaded.")
                    rewardedAd = ad
                    notifyPending(loaded = true)
                }
            }
        )
    }

    private fun notifyPending(loaded : Boolean)
    {
        val callbacks = synchronized(pendingCallbacks) {
            pendingCallbacks.toList().also { pendingCallbacks.clear() }
        }
        callbacks.forEach { (onAdLoaded, onAdFailed) -> if (loaded) onAdLoaded() else onAdFailed() }
    }

    private fun scheduleRetry()
    {
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.d(tag, "giving up after $retryCount attempts")
            return
        }
        val delayMs = RETRY_BASE_DELAY_MS shl retryCount
        retryCount++
        Log.d(tag, "retry $retryCount/$MAX_RETRY_COUNT in $delayMs ms")
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            retryJob = null
            startLoad({}, {})
        }
    }

    override fun showAd(activity: Any?, onRewardEarned: (Int) -> Unit, onAdClosed: () ->Unit)
    {
        val act = activity as? Activity
        if (rewardedAd !=null && act != null)
        {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback()
            {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(tag, "Ad was dismissed.")
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

        /** Ponowienia: 10 s, 20 s, 40 s, 80 s, 160 s, potem stop do kolejnej akcji uzytkownika. */
        private const val MAX_RETRY_COUNT = 5
        private const val RETRY_BASE_DELAY_MS = 10_000L
    }
}
