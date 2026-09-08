package com.minhajul.riftreign

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var gameView: RiftReignGameView
    private lateinit var economyStore: EconomyStore
    private lateinit var monetizationManager: MonetizationManager
    private lateinit var socialManager: SocialManager
    private var predictiveBackCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge first. Fullscreen hiding happens only after setContentView so
        // Android 15/16 always has an attached DecorView/WindowInsetsController.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.BLACK
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.BLACK
        }

        economyStore = EconomyStore(this)
        monetizationManager = MonetizationManager(this, economyStore) {
            if (::gameView.isInitialized) gameView.onMonetizationChanged()
        }
        socialManager = SocialManager(this) {
            if (::gameView.isInitialized) gameView.onSocialChanged()
        }
        gameView = RiftReignGameView(this, economyStore, monetizationManager, socialManager)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(4, 6, 18))
            addView(
                gameView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)
        registerPredictiveBack()
        root.post { hideSystemUi() }

        monetizationManager.start()
        socialManager.start()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) {
            gameView.post {
                gameView.onHostResume()
                hideSystemUi()
            }
            if (::monetizationManager.isInitialized) monetizationManager.onResume()
            if (::socialManager.isInitialized) socialManager.onResume()
        }
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            predictiveBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            predictiveBackCallback = null
        }
        if (::gameView.isInitialized) gameView.onHostDestroy()
        if (::monetizationManager.isInitialized) monetizationManager.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.post { hideSystemUi() }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Android 12L and lower fallback. Android 13+ uses OnBackInvokedDispatcher.
        if (::gameView.isInitialized && gameView.handleBackPressed()) return
        super.onBackPressed()
    }

    private fun registerPredictiveBack() {
        if (Build.VERSION.SDK_INT < 33) return
        val callback = OnBackInvokedCallback {
            if (!gameView.handleBackPressed()) finish()
        }
        predictiveBackCallback = callback
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
    }

    private fun hideSystemUi() {
        val decorView = window.decorView
        if (Build.VERSION.SDK_INT >= 30) {
            decorView.windowInsetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
