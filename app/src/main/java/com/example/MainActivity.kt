package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ads.AdMobManager
import com.example.ui.VirtualDjScreen
import com.example.ui.VirtualDjViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VirtualDjViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on during DJ performances
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Google AdMob SDK
        AdMobManager.initialize(this)

        setContent {
            MyApplicationTheme {
                VirtualDjScreen(
                    viewModel = viewModel,
                    onShowInterstitial = {
                        AdMobManager.showInterstitial(this)
                    }
                )
            }
        }
    }
}
