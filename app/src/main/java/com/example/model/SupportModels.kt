package com.example.model

import android.graphics.Bitmap

enum class AppLanguage {
  BANGLA,
  ENGLISH,
}

enum class MainTab {
  THIS_DEVICE,
  REMOTE_CONTROL,
  CHAT,
}

enum class SessionRole {
  IDLE,
  HOST,
  CONTROLLER,
}

enum class ConnectionState {
  DISCONNECTED,
  LISTENING,
  CONNECTING,
  AWAITING_CONSENT,
  CONNECTED,
}

enum class RemoteNavCommand(val wireCode: String) {
  BACK("BACK"),
  HOME("HOME"),
  RECENTS("RECENTS"),
  NOTIFICATIONS("NOTIFICATIONS"),
}

data class ActiveCapabilities(
  val screenSharing: Boolean = false,
  val remoteControlGranted: Boolean = false,
  val voiceCallActive: Boolean = false,
  val cameraSharing: Boolean = false,
  val cameraFacingFront: Boolean = false,
  val brightnessPercent: Int = 60,
)

data class AuditLogEntry(
  val id: String,
  val timeText: String,
  val messageBn: String,
  val messageEn: String,
  val isAlert: Boolean = false,
)

data class ChatMessage(
  val id: String,
  val senderName: String,
  val text: String,
  val timeText: String,
  val isLocal: Boolean,
)

data class IncomingConsentRequest(
  val peerName: String,
  val sessionCode: String,
  val peerAddress: String,
)

data class PermissionStatus(
  val screenCaptureActive: Boolean = false,
  val accessibilityEnabled: Boolean = false,
  val cameraGranted: Boolean = false,
  val micGranted: Boolean = false,
  val notificationsGranted: Boolean = true,
  val writeSettingsGranted: Boolean = false,
)

data class SupportUiState(
  val language: AppLanguage = AppLanguage.BANGLA,
  val activeTab: MainTab = MainTab.THIS_DEVICE,
  val deviceName: String = "",
  val temporaryCode: String = "",
  val localIpAddress: String = "127.0.0.1",
  val localPort: Int = 8765,
  val role: SessionRole = SessionRole.IDLE,
  val connectionState: ConnectionState = ConnectionState.LISTENING,
  val peerDeviceName: String = "",
  val peerEndpoint: String = "",
  val sessionDurationSeconds: Long = 0L,
  val capabilities: ActiveCapabilities = ActiveCapabilities(),
  val permissions: PermissionStatus = PermissionStatus(),
  val incomingConsent: IncomingConsentRequest? = null,
  val remoteScreenFrame: Bitmap? = null,
  val remoteCameraFrame: Bitmap? = null,
  val auditLogs: List<AuditLogEntry> = emptyList(),
  val chatMessages: List<ChatMessage> = emptyList(),
  val statusBannerError: String? = null,
  val lastLinkCopiedAt: Long = 0L,
)

object WirePacketType {
  const val HANDSHAKE_REQ: Byte = 1
  const val HANDSHAKE_ACK: Byte = 2
  const val HANDSHAKE_REJECT: Byte = 3
  const val SCREEN_FRAME: Byte = 4
  const val CAMERA_FRAME: Byte = 5
  const val AUDIO_PCM: Byte = 6
  const val NAV_COMMAND: Byte = 7
  const val TAP_GESTURE: Byte = 8
  const val BRIGHTNESS: Byte = 9
  const val CHAT: Byte = 10
  const val CAPABILITY_SYNC: Byte = 11
  const val DISCONNECT: Byte = 12
}
