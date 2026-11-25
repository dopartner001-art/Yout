package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.AppCopy
import com.example.model.ConnectionState
import com.example.model.SupportUiState
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.EmeraldSoftBg
import com.example.ui.theme.EmeraldSoftText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LightCardBg
import com.example.ui.theme.LightCardSubtle
import com.example.ui.theme.LightCardSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardElevated
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateTextMuted

@Composable
fun ThisDeviceScreen(
  state: SupportUiState,
  copy: AppCopy,
  onShareLink: () -> Unit,
  onOpenConnectDialog: () -> Unit,
  onRegenerateCode: () -> Unit,
  onCopyCode: () -> Unit,
  onOpenManualSignal: () -> Unit,
  onToggleScreenShare: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onRequestMediaPermissions: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
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
      // Hero Action Card ("অন্যের মোবাইল কন্ট্রোল ও সহায়তা করুন")
      item {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = LightCardSurface,
          tonalElevation = 4.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
          ) {
            Text(
              text = copy.heroTitle,
              style = MaterialTheme.typography.headlineSmall,
              color = LightTextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = copy.heroBody,
              style = MaterialTheme.typography.bodyMedium,
              color = LightTextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = onShareLink,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MidnightBg,
                contentColor = PureWhite,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("share_support_link_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = copy.createShareLinkBtn,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
              onClick = onOpenConnectDialog,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, LightTextSecondary.copy(alpha = 0.35f)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = LightTextPrimary),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("open_connect_dialog_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Dialpad,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = copy.connectWithCodeBtn,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      // Temporary Code & Local P2P Host Card
      item {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = LightCardBg,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.CloudQueue,
                contentDescription = null,
                tint = LightTextSecondary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              val statusPrefix = if (state.connectionState == ConnectionState.CONNECTED) {
                copy.codeCardConnectedPrefix
              } else {
                copy.codeCardWaitingPrefix
              }
              val accessHint = if (state.permissions.accessibilityEnabled) {
                copy.enabledLabel
              } else {
                copy.permAccessibilityTitle
              }
              Text(
                text = "$statusPrefix · $accessHint",
                style = MaterialTheme.typography.bodyMedium,
                color = LightTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = copy.tempCodeTitle,
              style = MaterialTheme.typography.bodySmall,
              color = LightTextSecondary,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 9-Digit Temporary Code Display + Regenerate Button
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MidnightCardElevated,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = PureWhite.copy(alpha = 0.14f),
                ) {
                  Text(
                    text = "123",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = state.temporaryCode.ifBlank { "--- --- ---" },
                  fontFamily = JetBrainsMonoFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 24.sp,
                  letterSpacing = 2.sp,
                  color = PureWhite,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("temporary_session_code_text"),
                )
                IconButton(
                  onClick = onRegenerateCode,
                  modifier = Modifier.testTag("regenerate_code_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = copy.regenerateCodeDesc,
                    tint = CyanGlow,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = onCopyCode,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MidnightCard,
                  contentColor = PureWhite,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("copy_code_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.ContentCopy,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = copy.copyCodeBtn,
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              OutlinedButton(
                onClick = onShareLink,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LightTextSecondary.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LightTextPrimary),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("share_code_link_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Share,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = copy.shareCodeLinkBtn,
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Local P2P Host Green Pill
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = EmeraldSoftBg,
              border = BorderStroke(1.dp, EmeraldLive.copy(alpha = 0.45f)),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenManualSignal)
                .testTag("manual_signal_pill"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Rounded.WifiTethering,
                  contentDescription = null,
                  tint = EmeraldSoftText,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "${copy.p2pHostReadyPrefix} ${state.localIpAddress}:${state.localPort}",
                  fontFamily = JetBrainsMonoFontFamily,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                  color = EmeraldSoftText,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Icon(
                  imageVector = Icons.Rounded.QrCode2,
                  contentDescription = copy.manualSignalBtn,
                  tint = EmeraldSoftText,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = copy.p2pModeFootnote,
              style = MaterialTheme.typography.bodySmall,
              color = LightTextSecondary,
            )
          }
        }
      }

      // Permissions & Transparency Setup Section
      item {
        Text(
          text = copy.permissionsSectionTitle,
          style = MaterialTheme.typography.titleMedium,
          color = SlateTextMuted,
          modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
      }

      item {
        PermissionSetupCard(
          icon = Icons.Rounded.ScreenShare,
          title = copy.permScreenShareTitle,
          subtitle = copy.permScreenShareDesc,
          isActive = state.permissions.screenCaptureActive,
          activeLabel = copy.stopLabel,
          inactiveLabel = copy.enableLabel,
          testTag = "perm_screen_share_card",
          onClick = onToggleScreenShare,
        )
      }

      item {
        PermissionSetupCard(
          icon = Icons.Rounded.AccessibilityNew,
          title = copy.permAccessibilityTitle,
          subtitle = copy.permAccessibilityDesc,
          isActive = state.permissions.accessibilityEnabled,
          activeLabel = copy.enabledLabel,
          inactiveLabel = copy.enableLabel,
          testTag = "perm_accessibility_card",
          onClick = onOpenAccessibilitySettings,
        )
      }

      item {
        PermissionSetupCard(
          icon = Icons.Rounded.Videocam,
          title = copy.permCameraMicTitle,
          subtitle = copy.permCameraMicDesc,
          isActive = state.permissions.cameraGranted && state.permissions.micGranted,
          activeLabel = copy.enabledLabel,
          inactiveLabel = copy.enableLabel,
          testTag = "perm_camera_mic_card",
          onClick = onRequestMediaPermissions,
        )
      }

      item {
        PermissionSetupCard(
          icon = Icons.Rounded.NotificationsActive,
          title = copy.permNotificationsTitle,
          subtitle = copy.permNotificationsDesc,
          isActive = state.permissions.notificationsGranted,
          activeLabel = copy.enabledLabel,
          inactiveLabel = copy.enableLabel,
          testTag = "perm_notifications_card",
          onClick = onRequestNotificationPermission,
        )
      }

      item {
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
private fun PermissionSetupCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  isActive: Boolean,
  activeLabel: String,
  inactiveLabel: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MidnightCard,
    border = BorderStroke(
      1.dp,
      if (isActive) EmeraldLive.copy(alpha = 0.5f) else MidnightOutline,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(if (isActive) EmeraldLive.copy(alpha = 0.16f) else LightCardSubtle.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isActive) EmeraldLive else CyanGlow,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = PureWhite,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = SlateTextMuted,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Surface(
        shape = RoundedCornerShape(50),
        color = if (isActive) EmeraldLive.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.22f),
        border = BorderStroke(
          1.dp,
          if (isActive) EmeraldLive else ElectricBlue,
        ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isActive) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = EmeraldLive,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
          }
          Text(
            text = if (isActive) activeLabel else inactiveLabel,
            style = MaterialTheme.typography.labelMedium,
            color = PureWhite,
          )
        }
      }
    }
  }
}
