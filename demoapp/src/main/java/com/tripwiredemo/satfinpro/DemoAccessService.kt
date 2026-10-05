package com.tripwiredemo.satfinpro

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Does nothing. It exists so Tripwire's banking-malware alarm can be shown: real OTP-stealing
 * apps ask for exactly this access. It receives no events and reads nothing.
 */
class DemoAccessService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit
    override fun onInterrupt() = Unit
}
