package com.example.ui.components

import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ads.AdMobManager
import com.example.ui.theme.DjBackground
import com.example.ui.theme.DjSurfaceBorder
import com.example.ui.theme.TextMuted
import com.google.android.gms.ads.*

private const val TAG = "AdMobBannerView"

@Composable
fun AdMobBannerView(
    modifier: Modifier = Modifier
) {
    var isAdLoaded by remember { mutableStateOf(false) }
    var adError by remember { mutableStateOf<String?>(null) }
    var currentAdUnitId by remember { mutableStateOf(AdMobManager.getBannerAdUnitId()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DjBackground)
            .border(1.dp, DjSurfaceBorder)
            .testTag("admob_banner_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Subtle ad label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ADVERTISEMENT",
                fontSize = 7.sp,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
            if (adError != null) {
                Text(
                    text = adError ?: "",
                    fontSize = 7.sp,
                    color = TextMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            contentAlignment = Alignment.Center
        ) {
            key(currentAdUnitId) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        try {
                            AdView(context).apply {
                                setAdSize(AdSize.BANNER)
                                adUnitId = currentAdUnitId
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                adListener = object : AdListener() {
                                    override fun onAdLoaded() {
                                        isAdLoaded = true
                                        adError = null
                                    }

                                    override fun onAdFailedToLoad(error: LoadAdError) {
                                        isAdLoaded = false
                                        adError = "Ad code: ${error.code}"
                                        Log.w(TAG, "Banner failed to load ($currentAdUnitId): ${error.message}")
                                        // If real banner fails to load or no fill, switch safely to test banner via key recreation
                                        if (currentAdUnitId != AdMobManager.TEST_BANNER_AD_UNIT_ID) {
                                            currentAdUnitId = AdMobManager.TEST_BANNER_AD_UNIT_ID
                                        }
                                    }
                                }
                                loadAd(AdRequest.Builder().build())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error instantiating AdView", e)
                            adError = "Init fallback"
                            // Return empty dummy view if WebView cannot initialize in emulator
                            android.view.View(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    0
                                )
                            }
                        }
                    },
                    onRelease = { view ->
                        if (view is AdView) {
                            try {
                                view.destroy()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error destroying AdView", e)
                            }
                        }
                    }
                )
            }

            if (!isAdLoaded && adError == null) {
                Text(
                    text = "Loading Google AdMob Banner...",
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }
    }
}
