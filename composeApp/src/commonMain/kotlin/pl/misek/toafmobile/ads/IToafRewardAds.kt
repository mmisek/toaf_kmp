package pl.misek.toafmobile.ads

interface IToafRewardAds {
    fun isAdLoaded() : Boolean
    fun loadAd(onAdLoaded : () -> Unit, onAdFailed : () -> Unit)
    fun showAd(activity: Any?, onRewardEarned: (Int) -> Unit, onAdClosed: () ->Unit)
}