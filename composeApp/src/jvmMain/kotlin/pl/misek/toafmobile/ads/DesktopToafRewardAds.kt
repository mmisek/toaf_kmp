package pl.misek.toafmobile.ads

class DesktopToafRewardAds : IToafRewardAds {
    override fun isAdLoaded() = false

    override fun loadAd(onAdLoaded: () -> Unit, onAdFailed: () -> Unit) {

    }

    override fun showAd(
        activity: Any?,
        onRewardEarned: (Int) -> Unit,
        onAdClosed: () -> Unit
    ) {

    }
}