package com.yohboostel.jardim

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var garden3D: Garden3DView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.navigationBarColor = android.graphics.Color.rgb(18, 49, 43)
        window.statusBarColor = android.graphics.Color.rgb(18, 49, 43)

        val root = FrameLayout(this)
        garden3D = Garden3DView(this)
        val game = GardenGameView(this)
        game.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        root.addView(garden3D, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(game, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (::garden3D.isInitialized) garden3D.onResume()
    }

    override fun onPause() {
        if (::garden3D.isInitialized) garden3D.onPause()
        super.onPause()
    }
}
