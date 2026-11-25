package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.model.RemoteNavCommand
import com.example.session.SupportSessionController

class YoutRemoteAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    SupportSessionController.refreshPermissions(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // No passive monitoring; commands execute only on explicit active session requests.
  }

  override fun onInterrupt() {
    // No-op
  }

  override fun onDestroy() {
    if (instance == this) {
      instance = null
    }
    SupportSessionController.refreshPermissions(this)
    super.onDestroy()
  }

  fun executeNavigationCommand(command: RemoteNavCommand): Boolean {
    val action = when (command) {
      RemoteNavCommand.BACK -> GLOBAL_ACTION_BACK
      RemoteNavCommand.HOME -> GLOBAL_ACTION_HOME
      RemoteNavCommand.RECENTS -> GLOBAL_ACTION_RECENTS
      RemoteNavCommand.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
    }
    return performGlobalAction(action)
  }

  fun executeNormalizedTap(xNorm: Float, yNorm: Float): Boolean {
    val metrics = resources.displayMetrics
    val x = (xNorm.coerceIn(0.01f, 0.99f)) * metrics.widthPixels
    val y = (yNorm.coerceIn(0.01f, 0.99f)) * metrics.heightPixels
    val path = Path().apply { moveTo(x, y) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, null, null)
  }

  companion object {
    @Volatile
    var instance: YoutRemoteAccessibilityService? = null
      private set

    fun isEnabled(context: Context): Boolean {
      if (instance != null) return true
      val expected = ComponentName(context, YoutRemoteAccessibilityService::class.java).flattenToString()
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false
      return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
  }
}
