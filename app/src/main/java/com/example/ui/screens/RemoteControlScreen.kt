package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.i18n.AppCopy
import com.example.model.AppLanguage
import com.example.model.RemoteNavCommand
import com.example.model.SessionRole
import com.example.model.SupportUiState
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardElevated
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateTextMuted
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun RemoteControlScreen(
  state: SupportUiState,
  copy: AppCopy,
  onToggleScreenShare: () -> Unit,
  onSendNavCommand: (RemoteNavCommand) -> Unit,
  onSendRemoteTap: (Float, Float) -> Unit,
  onBrightnessChange: (Int) -> Unit,
  onToggleVoiceCall: () -> Unit,
  onToggleCamera: () -> Unit,
  onFlipCamera: () -> Unit,
  onLocalCameraFrame: (ByteArray) -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 680.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Live Screen View & Sharing Control
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MidnightCard,
          border = BorderStroke(1.dp, MidnightOutline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = copy.remoteScreenTitle,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                modifier = Modifier.weight(1f),
              )
              if (state.remoteScreenFrame != null) {
                Icon(
                  imageVector = Icons.Rounded.TouchApp,
                  contentDescription = null,
                  tint = CyanGlow,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val screenBmp = state.remoteScreenFrame
            if (screenBmp != null) {
              Image(
                bitmap = screenBmp.asImageBitmap(),
                contentDescription = copy.remoteScreenTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(9f / 16f)
                  .clip(RoundedCornerShape(14.dp))
                  .background(MidnightBg)
                  .pointerInput(screenBmp) {
                    detectTapGestures { offset ->
                      if (size.width > 0 && size.height > 0) {
                        val xNorm = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val yNorm = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        onSendRemoteTap(xNorm, yNorm)
                      }
                    }
                  }
                  .testTag("remote_screen_canvas"),
              )
            } else {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MidnightBg,
                border = BorderStroke(1.dp, MidnightOutline),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(20.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.ScreenShare,
                    contentDescription = null,
                    tint = if (state.capabilities.screenSharing) EmeraldLive else CyanGlow,
                    modifier = Modifier.size(36.dp),
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = if (state.role == SessionRole.CONTROLLER) {
                      copy.remoteScreenEmptyController
                    } else {
                      copy.remoteScreenEmptyHost
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMuted,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = onToggleScreenShare,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (state.permissions.screenCaptureActive) CoralAlert else ElectricBlue,
                contentColor = PureWhite,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("toggle_screen_share_button"),
            ) {
              Icon(
                imageVector = if (state.permissions.screenCaptureActive) {
                  Icons.Rounded.StopScreenShare
                } else {
                  Icons.Rounded.ScreenShare
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (state.permissions.screenCaptureActive) {
                  copy.stopScreenShareBtn
                } else {
                  copy.startScreenShareBtn
                },
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }

      // Remote Navigation & Brightness Card
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MidnightCard,
          border = BorderStroke(1.dp, MidnightOutline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = copy.remoteNavTitle,
              style = MaterialTheme.typography.titleMedium,
              color = PureWhite,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              NavTileButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = copy.navBack,
                testTag = "nav_back_button",
                modifier = Modifier.weight(1f),
                onClick = { onSendNavCommand(RemoteNavCommand.BACK) },
              )
              NavTileButton(
                icon = Icons.Rounded.Home,
                label = copy.navHome,
                testTag = "nav_home_button",
                modifier = Modifier.weight(1f),
                onClick = { onSendNavCommand(RemoteNavCommand.HOME) },
              )
              NavTileButton(
                icon = Icons.Rounded.DynamicFeed,
                label = copy.navRecents,
                testTag = "nav_recents_button",
                modifier = Modifier.weight(1f),
                onClick = { onSendNavCommand(RemoteNavCommand.RECENTS) },
              )
              NavTileButton(
                icon = Icons.Rounded.Notifications,
                label = copy.navNotifications,
                testTag = "nav_notifications_button",
                modifier = Modifier.weight(1f),
                onClick = { onSendNavCommand(RemoteNavCommand.NOTIFICATIONS) },
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
              text = "${copy.brightnessLabel} ${state.capabilities.brightnessPercent}%",
              style = MaterialTheme.typography.bodyMedium,
              color = SlateTextMuted,
            )
            Slider(
              value = state.capabilities.brightnessPercent.toFloat(),
              onValueChange = { onBrightnessChange(it.toInt()) },
              valueRange = 5f..100f,
              colors = SliderDefaults.colors(
                thumbColor = CyanGlow,
                activeTrackColor = CyanGlow,
                inactiveTrackColor = MidnightCardElevated,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("brightness_slider"),
            )
          }
        }
      }

      // Voice Call & Remote Camera Card
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MidnightCard,
          border = BorderStroke(1.dp, MidnightOutline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = copy.voiceAndCamTitle,
              style = MaterialTheme.typography.titleMedium,
              color = PureWhite,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = onToggleVoiceCall,
              shape = RoundedCornerShape(24.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (state.capabilities.voiceCallActive) CoralAlert else ElectricBlue,
                contentColor = PureWhite,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("voice_call_button"),
            ) {
              Icon(
                imageVector = if (state.capabilities.voiceCallActive) {
                  Icons.Rounded.CallEnd
                } else {
                  Icons.Rounded.Call
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (state.capabilities.voiceCallActive) {
                  copy.voiceCallStop
                } else {
                  copy.voiceCallStart
                },
                style = MaterialTheme.typography.labelLarge,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = onToggleCamera,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (state.capabilities.cameraSharing) EmeraldLive else CoralAlert,
                  contentColor = if (state.capabilities.cameraSharing) MidnightBg else PureWhite,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("camera_toggle_button"),
              ) {
                Icon(
                  imageVector = if (state.capabilities.cameraSharing) {
                    Icons.Rounded.Videocam
                  } else {
                    Icons.Rounded.VideocamOff
                  },
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (state.capabilities.cameraSharing) copy.cameraStop else copy.cameraStart,
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              OutlinedButton(
                onClick = onFlipCamera,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MidnightOutline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanGlow),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("camera_flip_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Cameraswitch,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = copy.cameraSwitch,
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val remoteCam = state.remoteCameraFrame
            when {
              state.capabilities.cameraSharing && state.permissions.cameraGranted -> {
                CameraXPreviewBox(
                  useFrontCamera = state.capabilities.cameraFacingFront,
                  onFrameCaptured = onLocalCameraFrame,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp)),
                )
              }
              remoteCam != null -> {
                Image(
                  bitmap = remoteCam.asImageBitmap(),
                  contentDescription = copy.voiceAndCamTitle,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp)),
                )
              }
              else -> {
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MidnightBg,
                  border = BorderStroke(1.dp, MidnightOutline),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.VideocamOff,
                      contentDescription = null,
                      tint = SlateTextMuted,
                      modifier = Modifier.size(30.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = copy.cameraOffPreview,
                      style = MaterialTheme.typography.bodySmall,
                      color = SlateTextMuted,
                      textAlign = TextAlign.Center,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Transparent Activity Audit Log
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MidnightCard,
          border = BorderStroke(1.dp, MidnightOutline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = CyanGlow,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = copy.activityLogTitle,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
              )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (state.auditLogs.isEmpty()) {
              Text(
                text = copy.activityLogEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextMuted,
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.auditLogs.take(8).forEach { entry ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                  ) {
                    Text(
                      text = entry.timeText,
                      fontFamily = JetBrainsMonoFontFamily,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (entry.isAlert) CoralAlert else CyanGlow,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = if (state.language == AppLanguage.BANGLA) entry.messageBn else entry.messageEn,
                      style = MaterialTheme.typography.bodySmall,
                      color = PureWhite,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun NavTileButton(
  icon: ImageVector,
  label: String,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MidnightCardElevated,
    border = BorderStroke(1.dp, MidnightOutline),
    modifier = modifier
      .height(74.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 4.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = CyanGlow,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = PureWhite,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun CameraXPreviewBox(
  useFrontCamera: Boolean,
  onFrameCaptured: (ByteArray) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember { PreviewView(context) }
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  var lastFrameMs by remember { mutableLongStateOf(0L) }

  DisposableEffect(useFrontCamera) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    val mainExecutor = ContextCompat.getMainExecutor(context)

    providerFuture.addListener({
      runCatching {
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { imageAnalysis ->
            imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
              try {
                val now = System.currentTimeMillis()
                if (now - lastFrameMs >= 160L) {
                  lastFrameMs = now
                  val yBuffer = proxy.planes[0].buffer
                  val uBuffer = proxy.planes[1].buffer
                  val vBuffer = proxy.planes[2].buffer
                  val ySize = yBuffer.remaining()
                  val uSize = uBuffer.remaining()
                  val vSize = vBuffer.remaining()
                  val nv21 = ByteArray(ySize + uSize + vSize)
                  yBuffer.get(nv21, 0, ySize)
                  vBuffer.get(nv21, ySize, vSize)
                  uBuffer.get(nv21, ySize + vSize, uSize)
                  val yuvImage = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
                  val rawOut = ByteArrayOutputStream()
                  yuvImage.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 45, rawOut)
                  val rawBytes = rawOut.toByteArray()
                  val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                  if (bmp != null) {
                    val scaled = Bitmap.createScaledBitmap(bmp, 360, (360 * bmp.height / bmp.width.coerceAtLeast(1)).coerceAtLeast(240), true)
                    val jpgOut = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 50, jpgOut)
                    if (scaled != bmp) scaled.recycle()
                    bmp.recycle()
                    onFrameCaptured(jpgOut.toByteArray())
                  }
                }
              } catch (_: Exception) {
              } finally {
                proxy.close()
              }
            }
          }
        val selector = if (useFrontCamera) {
          CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
          CameraSelector.DEFAULT_BACK_CAMERA
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
      }
    }, mainExecutor)

    onDispose {
      runCatching {
        providerFuture.get().unbindAll()
      }
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = modifier,
  )
}
