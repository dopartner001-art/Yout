package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.session.SupportSessionController
import java.io.ByteArrayOutputStream

class YoutSupportService : Service() {

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var lastFrameSentMs = 0L
  private var sessionKeepAliveActive = false
  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  private val projectionCallback = object : MediaProjection.Callback() {
    override fun onStop() {
      stopScreenCaptureOnly(keepSessionService = sessionKeepAliveActive)
    }

    override fun onCapturedContentResize(width: Int, height: Int) {
      if (width > 0 && height > 0) {
        recreateCaptureSurface(width, height)
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_DISCONNECT_FROM_NOTIFICATION -> {
        sessionKeepAliveActive = false
        stopScreenCaptureOnly(keepSessionService = false)
        SupportSessionController.disconnectSession("Disconnected from notification")
        return START_NOT_STICKY
      }
      ACTION_STOP_ALL -> {
        sessionKeepAliveActive = false
        stopScreenCaptureOnly(keepSessionService = false)
        return START_NOT_STICKY
      }
      ACTION_START_SESSION_KEEPALIVE -> {
        sessionKeepAliveActive = true
        acquireLocks()
        promoteForeground(withMediaProjection = mediaProjection != null)
      }
      ACTION_STOP_SCREEN_SHARE -> {
        stopScreenCaptureOnly(keepSessionService = sessionKeepAliveActive)
      }
      ACTION_START_SCREEN_SHARE -> {
        sessionKeepAliveActive = true
        acquireLocks()
        promoteForeground(withMediaProjection = true)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
          intent.getParcelableExtra(EXTRA_DATA_INTENT)
        }

        if (resultCode == Activity.RESULT_OK && dataIntent != null) {
          startMediaProjection(resultCode, dataIntent)
        } else {
          stopScreenCaptureOnly(keepSessionService = sessionKeepAliveActive)
        }
      }
    }
    return START_STICKY
  }

  private fun promoteForeground(withMediaProjection: Boolean) {
    ensureNotificationChannel()
    val notification = buildTransparencyNotification(isScreenSharing = withMediaProjection)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val fgsType = if (withMediaProjection) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      }
      startForeground(NOTIFICATION_ID, notification, fgsType)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun acquireLocks() {
    if (wakeLock == null) {
      val pm = getSystemService(PowerManager::class.java)
      wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yout::SessionWakeLock").apply {
        setReferenceCounted(false)
        acquire(6 * 60 * 60 * 1000L)
      }
    }
    if (wifiLock == null) {
      val wm = applicationContext.getSystemService(WifiManager::class.java)
      @Suppress("DEPRECATION")
      val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
      } else {
        WifiManager.WIFI_MODE_FULL_HIGH_PERF
      }
      wifiLock = wm.createWifiLock(mode, "Yout::SessionWifiLock").apply {
        setReferenceCounted(false)
        acquire()
      }
    }
  }

  private fun releaseLocks() {
    runCatching {
      if (wakeLock?.isHeld == true) wakeLock?.release()
    }
    wakeLock = null
    runCatching {
      if (wifiLock?.isHeld == true) wifiLock?.release()
    }
    wifiLock = null
  }

  private fun startMediaProjection(resultCode: Int, dataIntent: Intent) {
    val manager = getSystemService(MediaProjectionManager::class.java)
    val projection = manager.getMediaProjection(resultCode, dataIntent)
    if (projection == null) {
      stopScreenCaptureOnly(keepSessionService = sessionKeepAliveActive)
      return
    }
    mediaProjection = projection
    projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

    val (width, height, dpi) = resolveTargetCaptureDimensions()
    createVirtualDisplayPipeline(projection, width, height, dpi)
    SupportSessionController.onScreenSharingStateChanged(active = true)
    promoteForeground(withMediaProjection = true)
  }

  private fun recreateCaptureSurface(rawWidth: Int, rawHeight: Int) {
    val projection = mediaProjection ?: return
    val (targetW, targetH) = clampToStreamResolution(rawWidth, rawHeight)
    val dpi = resources.displayMetrics.densityDpi
    virtualDisplay?.release()
    imageReader?.close()
    createVirtualDisplayPipeline(projection, targetW, targetH, dpi)
  }

  private fun createVirtualDisplayPipeline(
    projection: MediaProjection,
    width: Int,
    height: Int,
    dpi: Int,
  ) {
    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    imageReader = reader
    reader.setOnImageAvailableListener({ ir ->
      val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
      try {
        val now = System.currentTimeMillis()
        if (now - lastFrameSentMs < 110L) return@setOnImageAvailableListener
        lastFrameSentMs = now

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmapWidth = width + rowPadding / pixelStride
        val rawBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)
        val cropped = if (bitmapWidth != width) {
          Bitmap.createBitmap(rawBitmap, 0, 0, width, height).also { rawBitmap.recycle() }
        } else {
          rawBitmap
        }

        val baos = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 52, baos)
        cropped.recycle()
        SupportSessionController.onLocalScreenFrameCaptured(baos.toByteArray())
      } catch (_: Exception) {
      } finally {
        image.close()
      }
    }, Handler(Looper.getMainLooper()))

    virtualDisplay = projection.createVirtualDisplay(
      "YoutSupportDisplay",
      width,
      height,
      dpi,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      reader.surface,
      null,
      null,
    )
  }

  private fun resolveTargetCaptureDimensions(): Triple<Int, Int, Int> {
    val wm = getSystemService(WindowManager::class.java)
    val (rawW, rawH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = wm.maximumWindowMetrics.bounds
      bounds.width() to bounds.height()
    } else {
      resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
    }
    val (scaledW, scaledH) = clampToStreamResolution(rawW, rawH)
    return Triple(scaledW, scaledH, resources.displayMetrics.densityDpi)
  }

  private fun clampToStreamResolution(rawW: Int, rawH: Int): Pair<Int, Int> {
    val maxDim = 720
    val scale = minOf(1f, maxDim.toFloat() / maxOf(rawW, rawH).coerceAtLeast(1))
    val w = ((rawW * scale).toInt() / 2) * 2
    val h = ((rawH * scale).toInt() / 2) * 2
    return w.coerceAtLeast(240) to h.coerceAtLeast(240)
  }

  private fun stopScreenCaptureOnly(keepSessionService: Boolean) {
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
    runCatching {
      mediaProjection?.unregisterCallback(projectionCallback)
      mediaProjection?.stop()
    }
    mediaProjection = null
    SupportSessionController.onScreenSharingStateChanged(active = false)
    if (keepSessionService) {
      promoteForeground(withMediaProjection = false)
    } else {
      releaseLocks()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Yout Active Support Session",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Shows when Yout has an active transparent remote support session."
      }
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }

  private fun buildTransparencyNotification(isScreenSharing: Boolean): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openPending = PendingIntent.getActivity(
      this,
      10,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val disconnectIntent = Intent(this, YoutSupportService::class.java).apply {
      action = ACTION_DISCONNECT_FROM_NOTIFICATION
    }
    val disconnectPending = PendingIntent.getService(
      this,
      11,
      disconnectIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val title = if (isScreenSharing) {
      "Yout সাপোর্ট লাইভ — স্ক্রিন শেয়ার হচ্ছে"
    } else {
      "Yout সেশন সংযুক্ত — ব্যাকগ্রাউন্ডে সক্রিয়"
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText("ডিসকানেক্ট না করা পর্যন্ত সক্রিয় থাকবে • উভয় পক্ষ দৃশ্যমান")
      .setOngoing(true)
      .setContentIntent(openPending)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "বিচ্ছিন্ন করুন / Disconnect",
        disconnectPending,
      )
      .build()
  }

  override fun onDestroy() {
    releaseLocks()
    runCatching { virtualDisplay?.release() }
    runCatching { imageReader?.close() }
    runCatching { mediaProjection?.stop() }
    super.onDestroy()
  }

  companion object {
    private const val CHANNEL_ID = "yout_support_transparency"
    private const val NOTIFICATION_ID = 4102
    const val ACTION_START_SESSION_KEEPALIVE = "com.example.action.START_SESSION_KEEPALIVE"
    const val ACTION_START_SCREEN_SHARE = "com.example.action.START_SCREEN_SHARE"
    const val ACTION_STOP_SCREEN_SHARE = "com.example.action.STOP_SCREEN_SHARE"
    const val ACTION_STOP_ALL = "com.example.action.STOP_ALL"
    const val ACTION_DISCONNECT_FROM_NOTIFICATION = "com.example.action.DISCONNECT_NOTIFICATION"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_DATA_INTENT = "extra_data_intent"

    fun startSessionKeepAlive(context: Context) {
      val intent = Intent(context, YoutSupportService::class.java).apply {
        action = ACTION_START_SESSION_KEEPALIVE
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun startCapture(context: Context, resultCode: Int, data: Intent) {
      val intent = Intent(context, YoutSupportService::class.java).apply {
        action = ACTION_START_SCREEN_SHARE
        putExtra(EXTRA_RESULT_CODE, resultCode)
        putExtra(EXTRA_DATA_INTENT, data)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stopCapture(context: Context) {
      val intent = Intent(context, YoutSupportService::class.java).apply {
        action = ACTION_STOP_SCREEN_SHARE
      }
      runCatching { context.startService(intent) }
    }

    fun stopAll(context: Context) {
      val intent = Intent(context, YoutSupportService::class.java).apply {
        action = ACTION_STOP_ALL
      }
      runCatching { context.startService(intent) }
    }
  }
}
