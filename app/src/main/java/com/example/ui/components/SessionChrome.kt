package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PanToolAlt
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.AppCopy
import com.example.model.ConnectionState
import com.example.model.MainTab
import com.example.model.SessionRole
import com.example.model.SupportUiState
import com.example.ui.theme.AmberWait
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralSoftBg
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardElevated
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateTextMuted

@Composable
fun YoutTopBar(
  state: SupportUiState,
  copy: AppCopy,
  onToggleLanguage: () -> Unit,
  onDisconnectClick: () -> Unit,
) {
  val isConnected = state.connectionState == ConnectionState.CONNECTED
  val isConnecting = state.connectionState == ConnectionState.CONNECTING

  Surface(
    color = MidnightBg,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_yout_foreground),
        contentDescription = copy.appName,
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MidnightOutline, RoundedCornerShape(12.dp)),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = copy.appName,
          style = MaterialTheme.typography.titleLarge,
          color = PureWhite,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = if (isConnected) EmeraldLive else CyanGlow,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isConnected) copy.subtitleConnected else copy.subtitleReady,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Language pill
      Surface(
        shape = RoundedCornerShape(50),
        color = MidnightCard,
        border = BorderStroke(1.dp, MidnightOutline),
        modifier = Modifier
          .clip(RoundedCornerShape(50))
          .clickable(onClick = onToggleLanguage)
          .testTag("language_toggle_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = copy.langBadge,
            tint = CyanGlow,
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = copy.langBadge,
            style = MaterialTheme.typography.labelMedium,
            color = PureWhite,
          )
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Live Status pill
      val (pillBg, dotColor, statusLabel) = when {
        isConnected -> Triple(Color(0xFF0E382B), EmeraldLive, copy.statusConnected)
        isConnecting || state.connectionState == ConnectionState.RECONNECTING ->
          Triple(Color(0xFF3A2D12), AmberWait, copy.statusConnecting)
        state.connectionState == ConnectionState.LISTENING ->
          Triple(Color(0xFF152749), CyanGlow, copy.statusWaiting)
        else -> Triple(CoralSoftBg, CoralAlert, copy.statusIdle)
      }
      Surface(
        shape = RoundedCornerShape(50),
        color = pillBg,
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.55f)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(dotColor),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = PureWhite,
          )
        }
      }

      if (isConnected || isConnecting || state.connectionState == ConnectionState.RECONNECTING || state.capabilities.screenSharing) {
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
          onClick = onDisconnectClick,
          modifier = Modifier.testTag("top_disconnect_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
            contentDescription = copy.disconnectNowShort,
            tint = CoralAlert,
          )
        }
      }
    }
  }
}

@Composable
fun TransparencyLiveBanner(
  state: SupportUiState,
  copy: AppCopy,
  onDisconnectClick: () -> Unit,
) {
  val active = state.connectionState == ConnectionState.CONNECTED ||
    state.connectionState == ConnectionState.RECONNECTING ||
    state.capabilities.screenSharing ||
    state.capabilities.voiceCallActive ||
    state.capabilities.cameraSharing

  AnimatedVisibility(
    visible = active,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
      shape = RoundedCornerShape(18.dp),
      color = Color.Transparent,
      border = BorderStroke(1.dp, EmeraldLive.copy(alpha = 0.55f)),
    ) {
      Column(
        modifier = Modifier
          .background(
            Brush.horizontalGradient(
              listOf(Color(0xFF0E2B2A), Color(0xFF13264B)),
            ),
          )
          .padding(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(EmeraldLive),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = copy.activeTransparencyTitle,
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldLive,
              )
              val roleText = when (state.role) {
                SessionRole.HOST -> copy.transparencyRoleHost
                SessionRole.CONTROLLER -> copy.transparencyRoleController
                SessionRole.IDLE -> copy.capScreenOn
              }
              Text(
                text = if (state.peerDeviceName.isNotBlank()) {
                  "$roleText • ${state.peerDeviceName}"
                } else {
                  roleText
                },
                style = MaterialTheme.typography.bodySmall,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          if (state.connectionState == ConnectionState.CONNECTED) {
            val mins = state.sessionDurationSeconds / 60
            val secs = state.sessionDurationSeconds % 60
            Text(
              text = String.format("%02d:%02d", mins, secs),
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelLarge,
              color = CyanGlow,
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (state.capabilities.screenSharing) {
            CapabilityChip(Icons.Rounded.ScreenShare, copy.capScreenOn)
          }
          if (state.capabilities.remoteControlGranted || state.permissions.guidedPointerReady) {
            CapabilityChip(Icons.Rounded.PanToolAlt, copy.capControlOn)
          }
          if (state.capabilities.voiceCallActive) {
            CapabilityChip(Icons.Rounded.Mic, copy.capVoiceOn)
          }
          if (state.capabilities.cameraSharing) {
            CapabilityChip(Icons.Rounded.Videocam, copy.capCameraOn)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = onDisconnectClick,
          colors = ButtonDefaults.buttonColors(
            containerColor = CoralAlert,
            contentColor = PureWhite,
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("banner_disconnect_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = copy.disconnectBtn,
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun CapabilityChip(icon: ImageVector, text: String) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MidnightCardElevated.copy(alpha = 0.9f),
    border = BorderStroke(1.dp, MidnightOutline),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = CyanGlow,
        modifier = Modifier.size(13.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = PureWhite,
      )
    }
  }
}

@Composable
fun YoutBottomNavBar(
  activeTab: MainTab,
  copy: AppCopy,
  onSelectTab: (MainTab) -> Unit,
) {
  NavigationBar(
    containerColor = MidnightSurface,
    tonalElevation = 0.dp,
    modifier = Modifier.navigationBarsPadding(),
  ) {
    val items = listOf(
      Triple(MainTab.THIS_DEVICE, Icons.Rounded.Devices, copy.tabThisDevice),
      Triple(MainTab.REMOTE_CONTROL, Icons.Rounded.ScreenshotMonitor, copy.tabRemoteControl),
      Triple(MainTab.CHAT, Icons.AutoMirrored.Rounded.Chat, copy.tabChat),
    )
    items.forEach { (tab, icon, label) ->
      val selected = activeTab == tab
      NavigationBarItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        icon = {
          Icon(
            imageVector = icon,
            contentDescription = label,
          )
        },
        label = {
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = PureWhite,
          selectedTextColor = PureWhite,
          indicatorColor = ElectricBlue.copy(alpha = 0.35f),
          unselectedIconColor = SlateTextMuted,
          unselectedTextColor = SlateTextMuted,
        ),
        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
      )
    }
  }
}

@Composable
fun YoutNavigationRail(
  activeTab: MainTab,
  copy: AppCopy,
  onSelectTab: (MainTab) -> Unit,
) {
  NavigationRail(
    containerColor = MidnightSurface,
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    val items = listOf(
      Triple(MainTab.THIS_DEVICE, Icons.Rounded.Devices, copy.tabThisDevice),
      Triple(MainTab.REMOTE_CONTROL, Icons.Rounded.ScreenshotMonitor, copy.tabRemoteControl),
      Triple(MainTab.CHAT, Icons.AutoMirrored.Rounded.Chat, copy.tabChat),
    )
    items.forEach { (tab, icon, label) ->
      NavigationRailItem(
        selected = activeTab == tab,
        onClick = { onSelectTab(tab) },
        icon = { Icon(imageVector = icon, contentDescription = label) },
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationRailItemDefaults.colors(
          selectedIconColor = PureWhite,
          selectedTextColor = PureWhite,
          indicatorColor = ElectricBlue.copy(alpha = 0.35f),
          unselectedIconColor = SlateTextMuted,
          unselectedTextColor = SlateTextMuted,
        ),
        modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
      )
    }
  }
}
