package com.yohboostel.jardim

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.navigationBarColor = android.graphics.Color.rgb(18, 49, 43)
        window.statusBarColor = android.graphics.Color.rgb(18, 49, 43)
        setContentView(GardenGameView(this))
    }
}
