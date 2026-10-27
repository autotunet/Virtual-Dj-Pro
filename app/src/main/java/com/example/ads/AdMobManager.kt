package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdMobManager {

    private const val TAG = "AdMobManager"

    // User's provided Banner Ad Unit ID (1.)
    const val BANNER_AD_UNIT_ID = "ca-app-pub-1495262574338316/2803163788"

    // User's provided Interstitial / Popup Ad Unit IDs (2. to 8.)
    val INTERSTITIAL_AD_UNIT_IDS = listOf(
        "ca-app-pub-1495262574338316/2903409205", // 2.
        "ca-app-pub-1495262574338316/2452733430", // 3.
        "ca-app-pub-1495262574338316/8970040322", // 4.
        "ca-app-pub-1495262574338316/1182179137", // 5.
        "ca-app-pub-1495262574338316/4962596335", // 6.
        "ca-app-pub-1495262574338316/2714752655", // 7.
        "ca-app-pub-1495262574338316/3503687777"  // 8.
    )

    // Google Test Ad Unit Fallbacks (in case live ad units return NO_FILL during development)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var currentInterstitialIndex = 0
    private var currentInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    private val _adStatus = MutableStateFlow("AdMob Initialized")
    val adStatus: StateFlow<String> = _adStatus.asStateFlow()

    private val _adsShownCount = MutableStateFlow(0)
    val adsShownCount: StateFlow<Int> = _adsShownCount.asStateFlow()

    // Test mode toggle: If true, loads test ads; if false, loads user's real live ad units
    var useTestAds = false

    fun initialize(context: Context) {
        try {
            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(requestConfig)

            MobileAds.initialize(context.applicationContext) { status ->
                _adStatus.value = "AdMob Ready"
                Log.d(TAG, "AdMob MobileAds initialized: $status")
                // Preload first interstitial ad
                loadNextInterstitial(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdMob initialization error", e)
            _adStatus.value = "Init error: ${e.message}"
        }
    }

    fun loadNextInterstitial(context: Context) {
        if (isAdLoading || currentInterstitialAd != null) return

        isAdLoading = true
        val adUnitId = if (useTestAds) {
            TEST_INTERSTITIAL_AD_UNIT_ID
        } else {
            INTERSTITIAL_AD_UNIT_IDS[currentInterstitialIndex % INTERSTITIAL_AD_UNIT_IDS.size]
        }

        _adStatus.value = "Loading Interstitial #${(currentInterstitialIndex % INTERSTITIAL_AD_UNIT_IDS.size) + 1}..."
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    currentInterstitialAd = interstitialAd
                    isAdLoading = false
                    _adStatus.value = "Interstitial Ad Ready (${adUnitId.takeLast(10)})"
                    Log.d(TAG, "Ad loaded successfully: $adUnitId")

                    interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            currentInterstitialAd = null
                            _adStatus.value = "Ad closed"
                            currentInterstitialIndex++
                            loadNextInterstitial(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            currentInterstitialAd = null
                            isAdLoading = false
                            _adStatus.value = "Show failed: ${adError.message}"
                            Log.e(TAG, "Failed to show ad: ${adError.message}")
                            loadNextInterstitial(context)
                        }

                        override fun onAdShowedFullScreenContent() {
                            _adsShownCount.value += 1
                            _adStatus.value = "Showing Interstitial Ad"
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    currentInterstitialAd = null
                    isAdLoading = false
                    Log.w(TAG, "Live ad failed to load ($adUnitId): ${loadAdError.message}. Code: ${loadAdError.code}")

                    if (!useTestAds) {
                        if (loadAdError.code == AdRequest.ERROR_CODE_NO_FILL && currentInterstitialIndex < INTERSTITIAL_AD_UNIT_IDS.size - 1) {
                            currentInterstitialIndex++
                            loadNextInterstitial(context)
                        } else {
                            currentInterstitialIndex = 0
                            loadFallbackTestAd(context)
                        }
                    } else {
                        _adStatus.value = "Ad Load Error: ${loadAdError.message}"
                    }
                }
            }
        )
    }

    private fun loadFallbackTestAd(context: Context) {
        if (isAdLoading || currentInterstitialAd != null) return
        isAdLoading = true
        _adStatus.value = "Loading Google Test Ad..."
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        currentInterstitialAd = interstitialAd
                        isAdLoading = false
                        _adStatus.value = "Google Test Interstitial Ready"
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        currentInterstitialAd = null
                        isAdLoading = false
                        _adStatus.value = "Test ad load failed: ${loadAdError.message}"
                        Log.w(TAG, "Test ad load failed: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isAdLoading = false
            Log.e(TAG, "Error in loadFallbackTestAd", e)
        }
    }

    fun showInterstitial(activity: Activity): Boolean {
        val ad = currentInterstitialAd
        if (ad != null) {
            ad.show(activity)
            return true
        } else {
            _adStatus.value = "Ad not ready yet. Preloading..."
            loadNextInterstitial(activity)
            return false
        }
    }

    fun getBannerAdUnitId(): String {
        return if (useTestAds) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID
    }
}
