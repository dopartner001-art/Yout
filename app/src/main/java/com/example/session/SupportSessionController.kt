package com.example.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.media.VoiceCallManager
import com.example.model.ActiveCapabilities
import com.example.model.AppLanguage
import com.example.model.AuditLogEntry
import com.example.model.ChatMessage
import com.example.model.ConnectionState
import com.example.model.GuidedPrompt
import com.example.model.IncomingConsentRequest
import com.example.model.MainTab
import com.example.model.RemoteNavCommand
import com.example.model.SessionRole
import com.example.model.SupportUiState
import com.example.model.TapMarker
import com.example.model.WirePacketType
import com.example.network.P2pTransportManager
import com.example.network.TransportListener
import com.example.network.WebBridgeListener
import com.example.network.WebBridgeServer
import com.example.service.YoutSupportService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object SupportSessionController : TransportListener, WebBridgeListener {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _uiState = MutableStateFlow(
    SupportUiState(
      deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    ),
  )
  val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

  private var transport: P2pTransportManager? = null
  private var webServer: WebBridgeServer? = null
  private var voiceManager: VoiceCallManager? = null
  private var pendingConsentDeferred: CompletableDeferred<Boolean>? = null
  private var timerJob: Job? = null
  private var appContext: Context? = null
  private var initialized = false

  @Volatile
  private var latestLocalScreenJpeg: ByteArray? = null

  fun initialize(context: Context) {
    appContext = context.applicationContext
    if (!initialized) {
      initialized = true
      val mgr = P2pTransportManager(this)
      mgr.localDeviceName = _uiState.value.deviceName.ifBlank { "Yout Phone" }
      transport = mgr

      val web = WebBridgeServer(this)
      webServer = web
      web.start(8766)

      voiceManager = VoiceCallManager(context.applicationContext) { chunk ->
        transport?.sendPacket(WirePacketType.AUDIO_PCM, chunk)
      }
      mgr.startHostServer()

      _uiState.update {
        it.copy(
          temporaryCode = mgr.expectedCode,
          localIpAddress = mgr.discoverLocalIpv4Address(),
          localPort = mgr.boundPort,
          webPort = web.boundPort,
          connectionState = ConnectionState.LISTENING,
        )
      }
      addAuditLog(
        bn = "টেম্পোরারি কোড তৈরি ও মোবাইল/ল্যাপটপ সাপোর্ট হোস্ট চালু হয়েছে",
        en = "Temporary session code ready & mobile/laptop host started",
      )
    }
    refreshPermissions(context)
  }

  fun refreshPermissions(context: Context) {
    val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
    val writeSettings = Settings.System.canWrite(context)

    _uiState.update { state ->
      state.copy(
        permissions = state.permissions.copy(
          guidedPointerReady = true,
          cameraGranted = cam,
          micGranted = mic,
          notificationsGranted = notif,
          writeSettingsGranted = writeSettings,
        ),
        localIpAddress = transport?.discoverLocalIpv4Address() ?: state.localIpAddress,
        webPort = webServer?.boundPort ?: state.webPort,
      )
    }
  }

  fun setLanguage(language: AppLanguage) {
    _uiState.update { it.copy(language = language) }
  }

  fun selectTab(tab: MainTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun regenerateTemporaryCode() {
    val newCode = transport?.regenerateCode() ?: return
    _uiState.update { it.copy(temporaryCode = newCode) }
    addAuditLog(
      bn = "নতুন টেম্পোরারি কোড তৈরি: $newCode",
      en = "Generated new temporary code: $newCode",
    )
  }

  fun buildShareLink(): String {
    val mgr = transport ?: return ""
    return mgr.buildDeepLink()
  }

  fun buildLaptopWebUrl(): String {
    val state = _uiState.value
    return "http://${state.localIpAddress}:${state.webPort}/?code=${state.temporaryCode}"
  }

  fun buildManualToken(): String {
    val mgr = transport ?: return ""
    return mgr.buildManualToken()
  }

  fun connectToPeer(hostIp: String, port: Int, code: String) {
    val formatted = P2pTransportManager.formatNineDigitCode(code)
    _uiState.update {
      it.copy(
        connectionState = ConnectionState.CONNECTING,
        statusBannerError = null,
      )
    }
    addAuditLog(
      bn = "$hostIp:$port এ কোড $formatted দিয়ে সংযুক্ত হওয়ার চেষ্টা চলছে...",
      en = "Connecting to $hostIp:$port with code $formatted...",
    )
    transport?.connectToHost(hostIp, port, formatted)
  }

  fun respondToIncomingConsent(accepted: Boolean) {
    val request = _uiState.value.incomingConsent
    _uiState.update { it.copy(incomingConsent = null) }
    pendingConsentDeferred?.complete(accepted)
    pendingConsentDeferred = null
    if (request != null) {
      if (accepted) {
        addAuditLog(
          bn = "${request.peerName} কে সেশন অনুমতি দেওয়া হয়েছে",
          en = "Approved support session for ${request.peerName}",
        )
      } else {
        addAuditLog(
          bn = "${request.peerName} এর সংযোগ অনুরোধ প্রত্যাখ্যান করা হয়েছে",
          en = "Declined support request from ${request.peerName}",
          isAlert = true,
        )
      }
    }
  }

  fun disconnectSession(reason: String = "Disconnected by user") {
    webServer?.clearActiveWebSession()
    appContext?.let { YoutSupportService.stopAll(it) }
    voiceManager?.stop()
    transport?.disconnect(reason)
  }

  fun onScreenSharingStateChanged(active: Boolean) {
    if (!active) latestLocalScreenJpeg = null
    _uiState.update { state ->
      state.copy(
        permissions = state.permissions.copy(screenCaptureActive = active),
        capabilities = state.capabilities.copy(screenSharing = active),
      )
    }
    syncCapabilitiesToPeer()
    addAuditLog(
      bn = if (active) "লাইভ স্ক্রিন শেয়ারিং শুরু হয়েছে" else "স্ক্রিন শেয়ারিং বন্ধ হয়েছে",
      en = if (active) "Live screen sharing started" else "Screen sharing stopped",
    )
  }

  fun onLocalScreenFrameCaptured(jpegBytes: ByteArray) {
    latestLocalScreenJpeg = jpegBytes
    if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
      transport?.sendPacket(WirePacketType.SCREEN_FRAME, jpegBytes)
    }
  }

  fun onLocalCameraFrameCaptured(jpegBytes: ByteArray) {
    if (_uiState.value.connectionState == ConnectionState.CONNECTED &&
      _uiState.value.capabilities.cameraSharing
    ) {
      transport?.sendPacket(WirePacketType.CAMERA_FRAME, jpegBytes)
    }
  }

  fun sendNavigationCommand(command: RemoteNavCommand) {
    if (_uiState.value.connectionState == ConnectionState.CONNECTED &&
      _uiState.value.role == SessionRole.CONTROLLER
    ) {
      transport?.sendPacket(WirePacketType.NAV_COMMAND, command.wireCode.toByteArray(Charsets.UTF_8))
      addAuditLog(
        bn = "গাইডেড নির্দেশনা পাঠানো হয়েছে: ${command.wireCode}",
        en = "Sent guided prompt: ${command.wireCode}",
      )
    } else {
      _uiState.update { it.copy(guidedNavPrompt = GuidedPrompt(command, System.currentTimeMillis())) }
      addAuditLog(
        bn = "গাইডেড নির্দেশনা: ${command.wireCode}",
        en = "Guided prompt: ${command.wireCode}",
      )
    }
  }

  fun sendRemoteTap(xNorm: Float, yNorm: Float) {
    if (_uiState.value.connectionState == ConnectionState.CONNECTED &&
      _uiState.value.role == SessionRole.CONTROLLER
    ) {
      val json = JSONObject()
        .put("x", xNorm.toDouble())
        .put("y", yNorm.toDouble())
        .toString()
      transport?.sendPacket(WirePacketType.TAP_GESTURE, json.toByteArray(Charsets.UTF_8))
      _uiState.update { it.copy(guidedTapMarker = TapMarker(xNorm, yNorm, System.currentTimeMillis())) }
      addAuditLog(
        bn = "স্ক্রিনে গাইডেড পয়েন্টার পাঠানো হয়েছে",
        en = "Sent guided tap pointer",
      )
    }
  }

  fun updateBrightness(percent: Int) {
    val clamped = percent.coerceIn(5, 100)
    _uiState.update {
      it.copy(capabilities = it.capabilities.copy(brightnessPercent = clamped))
    }
    if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
      transport?.sendPacket(WirePacketType.BRIGHTNESS, clamped.toString().toByteArray(Charsets.UTF_8))
    }
    applyLocalSystemBrightnessIfPermitted(clamped)
  }

  fun toggleVoiceCall(): Boolean {
    val currentlyOn = _uiState.value.capabilities.voiceCallActive
    val newState = if (currentlyOn) {
      voiceManager?.stop()
      false
    } else {
      voiceManager?.start() == true
    }
    _uiState.update {
      it.copy(capabilities = it.capabilities.copy(voiceCallActive = newState))
    }
    syncCapabilitiesToPeer()
    addAuditLog(
      bn = if (newState) "ভয়েস কল চালু হয়েছে" else "ভয়েস কল বন্ধ করা হয়েছে",
      en = if (newState) "Voice call started" else "Voice call ended",
    )
    return newState
  }

  fun setCameraSharing(enabled: Boolean) {
    _uiState.update {
      it.copy(capabilities = it.capabilities.copy(cameraSharing = enabled))
    }
    syncCapabilitiesToPeer()
    addAuditLog(
      bn = if (enabled) "রিমোট ক্যামেরা শেয়ার চালু" else "রিমোট ক্যামেরা বন্ধ",
      en = if (enabled) "Remote camera sharing enabled" else "Remote camera stopped",
    )
  }

  fun flipCameraFacing() {
    _uiState.update {
      it.copy(
        capabilities = it.capabilities.copy(
          cameraFacingFront = !it.capabilities.cameraFacingFront,
        ),
      )
    }
    addAuditLog(
      bn = if (_uiState.value.capabilities.cameraFacingFront) "সামনের ক্যামেরা নির্বাচিত" else "পিছনের ক্যামেরা নির্বাচিত",
      en = if (_uiState.value.capabilities.cameraFacingFront) "Switched to front camera" else "Switched to back camera",
    )
  }

  fun sendChatMessage(text: String) {
    val clean = text.trim()
    if (clean.isEmpty()) return
    val entry = ChatMessage(
      id = UUID.randomUUID().toString(),
      senderName = _uiState.value.deviceName.ifBlank { "Me" },
      text = clean,
      timeText = currentTimeLabel(),
      isLocal = true,
    )
    _uiState.update { it.copy(chatMessages = it.chatMessages + entry) }
    if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
      val payload = JSONObject()
        .put("sender", entry.senderName)
        .put("text", clean)
        .toString()
      transport?.sendPacket(WirePacketType.CHAT, payload.toByteArray(Charsets.UTF_8))
    }
  }

  fun clearErrorBanner() {
    _uiState.update { it.copy(statusBannerError = null) }
  }

  private fun syncCapabilitiesToPeer() {
    if (_uiState.value.connectionState != ConnectionState.CONNECTED) return
    val caps = _uiState.value.capabilities
    val json = JSONObject()
      .put("screen", caps.screenSharing)
      .put("control", true)
      .put("voice", caps.voiceCallActive)
      .put("camera", caps.cameraSharing)
      .toString()
    transport?.sendPacket(WirePacketType.CAPABILITY_SYNC, json.toByteArray(Charsets.UTF_8))
  }

  private fun applyLocalSystemBrightnessIfPermitted(percent: Int) {
    val ctx = appContext ?: return
    if (Settings.System.canWrite(ctx)) {
      val value = (percent * 255 / 100).coerceIn(10, 255)
      runCatching {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
      }
    }
  }

  private fun startSessionTimer() {
    timerJob?.cancel()
    timerJob = scope.launch {
      while (true) {
        delay(1000L)
        _uiState.update { it.copy(sessionDurationSeconds = it.sessionDurationSeconds + 1L) }
      }
    }
  }

  private fun stopSessionTimer() {
    timerJob?.cancel()
    timerJob = null
  }

  private fun addAuditLog(bn: String, en: String, isAlert: Boolean = false) {
    val item = AuditLogEntry(
      id = UUID.randomUUID().toString(),
      timeText = currentTimeLabel(),
      messageBn = bn,
      messageEn = en,
      isAlert = isAlert,
    )
    _uiState.update {
      it.copy(auditLogs = (listOf(item) + it.auditLogs).take(40))
    }
  }

  private fun currentTimeLabel(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

  // TransportListener callbacks
  override fun onHostListening(ipAddress: String, port: Int) {
    scope.launch {
      _uiState.update {
        it.copy(
          localIpAddress = ipAddress,
          localPort = port,
          webPort = webServer?.boundPort ?: it.webPort,
          connectionState = if (it.connectionState == ConnectionState.CONNECTED) {
            ConnectionState.CONNECTED
          } else {
            ConnectionState.LISTENING
          },
        )
      }
    }
  }

  override fun onIncomingConsent(
    peerName: String,
    code: String,
    peerIp: String,
  ): CompletableDeferred<Boolean> {
    val deferred = CompletableDeferred<Boolean>()
    pendingConsentDeferred = deferred
    scope.launch {
      _uiState.update {
        it.copy(
          incomingConsent = IncomingConsentRequest(
            peerName = peerName,
            sessionCode = code,
            peerAddress = peerIp,
          ),
        )
      }
      addAuditLog(
        bn = "$peerName ($peerIp) সংযুক্ত হওয়ার অনুমতি চেয়েছেন",
        en = "$peerName ($peerIp) requested permission to connect",
      )
    }
    return deferred
  }

  override fun onConnected(isHost: Boolean, peerName: String, peerEndpoint: String) {
    scope.launch {
      appContext?.let { YoutSupportService.startSessionKeepAlive(it) }
      _uiState.update {
        it.copy(
          role = if (isHost) SessionRole.HOST else SessionRole.CONTROLLER,
          connectionState = ConnectionState.CONNECTED,
          peerDeviceName = peerName,
          peerEndpoint = peerEndpoint,
          statusBannerError = null,
          activeTab = MainTab.REMOTE_CONTROL,
        )
      }
      if (timerJob?.isActive != true) {
        startSessionTimer()
      }
      syncCapabilitiesToPeer()
      addAuditLog(
        bn = "$peerName এর সাথে স্বচ্ছ সেশন সংযুক্ত হয়েছে",
        en = "Connected transparent session with $peerName",
      )
    }
  }

  override fun onReconnecting(peerEndpoint: String) {
    scope.launch {
      _uiState.update {
        it.copy(connectionState = ConnectionState.RECONNECTING)
      }
      addAuditLog(
        bn = "নেটওয়ার্ক পুনরুদ্ধার করা হচ্ছে ($peerEndpoint)...",
        en = "Reconnecting session ($peerEndpoint)...",
      )
    }
  }

  override fun onDisconnected(reason: String) {
    scope.launch {
      stopSessionTimer()
      voiceManager?.stop()
      webServer?.clearActiveWebSession()
      appContext?.let { YoutSupportService.stopAll(it) }
      _uiState.update {
        it.copy(
          role = SessionRole.IDLE,
          connectionState = ConnectionState.LISTENING,
          peerDeviceName = "",
          peerEndpoint = "",
          sessionDurationSeconds = 0L,
          remoteScreenFrame = null,
          remoteCameraFrame = null,
          guidedTapMarker = null,
          guidedNavPrompt = null,
          capabilities = ActiveCapabilities(),
          incomingConsent = null,
          permissions = it.permissions.copy(screenCaptureActive = false),
        )
      }
      addAuditLog(
        bn = "সেশন বিচ্ছিন্ন করা হয়েছে ($reason)",
        en = "Session disconnected ($reason)",
        isAlert = true,
      )
    }
  }

  override fun onPacketReceived(type: Byte, payload: ByteArray) {
    when (type) {
      WirePacketType.SCREEN_FRAME -> {
        val bmp = BitmapFactory.decodeByteArray(payload, 0, payload.size)
        if (bmp != null) {
          scope.launch { _uiState.update { it.copy(remoteScreenFrame = bmp) } }
        }
      }
      WirePacketType.CAMERA_FRAME -> {
        val bmp = BitmapFactory.decodeByteArray(payload, 0, payload.size)
        if (bmp != null) {
          scope.launch { _uiState.update { it.copy(remoteCameraFrame = bmp) } }
        }
      }
      WirePacketType.AUDIO_PCM -> {
        voiceManager?.playIncomingChunk(payload)
      }
      WirePacketType.NAV_COMMAND -> {
        val cmdName = String(payload, Charsets.UTF_8)
        val cmd = RemoteNavCommand.entries.find { it.wireCode == cmdName } ?: return
        scope.launch {
          _uiState.update { it.copy(guidedNavPrompt = GuidedPrompt(cmd, System.currentTimeMillis())) }
          addAuditLog(
            bn = "সহায়তাকারী নির্দেশনা দিয়েছেন: $cmdName চাপুন",
            en = "Helper guided you to press: $cmdName",
          )
        }
      }
      WirePacketType.TAP_GESTURE -> {
        val obj = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull() ?: return
        val x = obj.optDouble("x", 0.5).toFloat()
        val y = obj.optDouble("y", 0.5).toFloat()
        scope.launch {
          _uiState.update { it.copy(guidedTapMarker = TapMarker(x, y, System.currentTimeMillis())) }
          addAuditLog(
            bn = "সহায়তাকারী স্ক্রিনে পয়েন্টার (${(x * 100).toInt()}%, ${(y * 100).toInt()}%) দেখিয়েছেন",
            en = "Helper pointed on screen (${(x * 100).toInt()}%, ${(y * 100).toInt()}%)",
          )
        }
      }
      WirePacketType.BRIGHTNESS -> {
        val level = String(payload, Charsets.UTF_8).toIntOrNull() ?: return
        scope.launch {
          _uiState.update { it.copy(capabilities = it.capabilities.copy(brightnessPercent = level)) }
          applyLocalSystemBrightnessIfPermitted(level)
          addAuditLog(
            bn = "অপর পক্ষ ব্রাইটনেস $level% সেট করেছেন",
            en = "Peer adjusted brightness to $level%",
          )
        }
      }
      WirePacketType.CHAT -> {
        val obj = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull() ?: return
        val sender = obj.optString("sender", "Peer")
        val text = obj.optString("text", "")
        if (text.isNotBlank()) {
          scope.launch {
            val msg = ChatMessage(
              id = UUID.randomUUID().toString(),
              senderName = sender,
              text = text,
              timeText = currentTimeLabel(),
              isLocal = false,
            )
            _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }
          }
        }
      }
      WirePacketType.CAPABILITY_SYNC -> {
        val obj = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull() ?: return
        scope.launch {
          _uiState.update {
            it.copy(
              capabilities = it.capabilities.copy(
                screenSharing = obj.optBoolean("screen", it.capabilities.screenSharing),
                remoteControlGranted = obj.optBoolean("control", it.capabilities.remoteControlGranted),
                voiceCallActive = obj.optBoolean("voice", it.capabilities.voiceCallActive),
                cameraSharing = obj.optBoolean("camera", it.capabilities.cameraSharing),
              ),
            )
          }
        }
      }
    }
  }

  override fun onTransportError(message: String) {
    scope.launch {
      _uiState.update { it.copy(statusBannerError = message) }
      addAuditLog(
        bn = "নেটওয়ার্ক ত্রুটি: $message",
        en = "Network error: $message",
        isAlert = true,
      )
    }
  }

  // WebBridgeListener callbacks (Laptop browser support)
  override fun getExpectedSessionCode(): String = _uiState.value.temporaryCode

  override fun getHostDeviceName(): String = _uiState.value.deviceName.ifBlank { "Yout Phone" }

  override fun getLatestScreenFrame(): ByteArray? = latestLocalScreenJpeg

  override fun getChatHistoryJson(): JSONArray {
    val arr = JSONArray()
    _uiState.value.chatMessages.takeLast(30).forEach { msg ->
      arr.put(
        JSONObject()
          .put("sender", msg.senderName)
          .put("text", msg.text)
          .put("time", msg.timeText),
      )
    }
    return arr
  }

  override fun isSessionConnected(): Boolean = _uiState.value.connectionState == ConnectionState.CONNECTED

  override fun onWebIncomingConsent(
    peerName: String,
    code: String,
    peerIp: String,
  ): CompletableDeferred<Boolean> = onIncomingConsent(peerName, code, peerIp)

  override fun onWebConnected(peerName: String, peerEndpoint: String) {
    onConnected(isHost = true, peerName = peerName, peerEndpoint = peerEndpoint)
  }

  override fun onWebTapPointer(xNorm: Float, yNorm: Float) {
    scope.launch {
      _uiState.update { it.copy(guidedTapMarker = TapMarker(xNorm, yNorm, System.currentTimeMillis())) }
      addAuditLog(
        bn = "ল্যাপটপ থেকে স্ক্রিন পয়েন্টার (${(xNorm * 100).toInt()}%, ${(yNorm * 100).toInt()}%) পাঠানো হয়েছে",
        en = "Laptop pointed on screen (${(xNorm * 100).toInt()}%, ${(yNorm * 100).toInt()}%)",
      )
    }
  }

  override fun onWebNavPrompt(command: String) {
    val cmd = RemoteNavCommand.entries.find { it.wireCode == command } ?: return
    scope.launch {
      _uiState.update { it.copy(guidedNavPrompt = GuidedPrompt(cmd, System.currentTimeMillis())) }
      addAuditLog(
        bn = "ল্যাপটপ থেকে নির্দেশনা: ${cmd.wireCode} চাপুন",
        en = "Laptop guided you to press: ${cmd.wireCode}",
      )
    }
  }

  override fun onWebChatMessage(sender: String, text: String) {
    scope.launch {
      val msg = ChatMessage(
        id = UUID.randomUUID().toString(),
        senderName = sender,
        text = text,
        timeText = currentTimeLabel(),
        isLocal = false,
      )
      _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }
    }
  }

  override fun onWebDisconnected(reason: String) {
    onDisconnected(reason)
  }
}
