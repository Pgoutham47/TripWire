package com.tripwiredemo.satfinpro

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class DemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Tripwire demo app.\n\nThis is not a real trading app. It exists only to test Tripwire's install warning."
            textSize = 20f
            setPadding(48, 96, 48, 48)
        })
    }
}
