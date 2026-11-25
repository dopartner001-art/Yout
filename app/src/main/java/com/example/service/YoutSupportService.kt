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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

  private val projectionCallback = object : MediaProjection.Callback() {
    override fun onStop() {
      stopScreenCaptureInternal(disconnectSession = false)
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
        stopScreenCaptureInternal(disconnectSession = true)
        return START_NOT_STICKY
      }
      ACTION_STOP_SCREEN_SHARE -> {
        stopScreenCaptureInternal(disconnectSession = false)
        return START_NOT_STICKY
      }
      ACTION_START_SCREEN_SHARE -> {
        ensureNotificationChannel()
        val notification = buildTransparencyNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
          )
        } else {
          startForeground(NOTIFICATION_ID, notification)
        }

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
          stopScreenCaptureInternal(disconnectSession = false)
        }
      }
    }
    return START_NOT_STICKY
  }

  private fun startMediaProjection(resultCode: Int, dataIntent: Intent) {
    val manager = getSystemService(MediaProjectionManager::class.java)
    val projection = manager.getMediaProjection(resultCode, dataIntent)
    if (projection == null) {
      stopScreenCaptureInternal(disconnectSession = false)
      return
    }
    mediaProjection = projection
    projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

    val (width, height, dpi) = resolveTargetCaptureDimensions()
    createVirtualDisplayPipeline(projection, width, height, dpi)
    SupportSessionController.onScreenSharingStateChanged(active = true)
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

  private fun stopScreenCaptureInternal(disconnectSession: Boolean) {
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
    if (disconnectSession) {
      SupportSessionController.disconnectSession("Disconnected from notification")
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Yout Active Support Session",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Shows when Yout is actively sharing your screen with a connected helper."
      }
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }

  private fun buildTransparencyNotification(): Notification {
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

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Yout সাপোর্ট লাইভ — স্ক্রিন শেয়ার হচ্ছে")
      .setContentText("উভয় পক্ষ সেশন দেখতে পাচ্ছেন • যেকোনো সময় বিচ্ছিন্ন করুন")
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
    runCatching { virtualDisplay?.release() }
    runCatching { imageReader?.close() }
    runCatching { mediaProjection?.stop() }
    super.onDestroy()
  }

  companion object {
    private const val CHANNEL_ID = "yout_support_transparency"
    private const val NOTIFICATION_ID = 4102
    const val ACTION_START_SCREEN_SHARE = "com.example.action.START_SCREEN_SHARE"
    const val ACTION_STOP_SCREEN_SHARE = "com.example.action.STOP_SCREEN_SHARE"
    const val ACTION_DISCONNECT_FROM_NOTIFICATION = "com.example.action.DISCONNECT_NOTIFICATION"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_DATA_INTENT = "extra_data_intent"

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
      context.startService(intent)
    }
  }
}
