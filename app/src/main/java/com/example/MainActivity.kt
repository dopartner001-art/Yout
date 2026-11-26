package com.example

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.copyFor
import com.example.model.AppLanguage
import com.example.model.MainTab
import com.example.network.ParsedInvite
import com.example.network.P2pTransportManager
import com.example.service.YoutSupportService
import com.example.ui.SupportViewModel
import com.example.ui.components.TransparencyLiveBanner
import com.example.ui.components.YoutBottomNavBar
import com.example.ui.components.YoutNavigationRail
import com.example.ui.components.YoutTopBar
import com.example.ui.dialogs.ConnectCodeDialog
import com.example.ui.dialogs.IncomingConsentDialog
import com.example.ui.dialogs.ManualSignalingDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.RemoteControlScreen
import com.example.ui.screens.ThisDeviceScreen
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.YoutTheme

class MainActivity : ComponentActivity() {

  private val viewModel: SupportViewModel by viewModels()
  private var pendingDeepLinkInvite by mutableStateOf<ParsedInvite?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleDeepLinkIntent(intent)

    setContent {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val isBangla = state.language == AppLanguage.BANGLA

      YoutTheme(isBangla = isBangla) {
        YoutAppRoot(
          viewModel = viewModel,
          deepLinkInvite = pendingDeepLinkInvite,
          onConsumeDeepLink = { pendingDeepLinkInvite = null },
          onShareText = { textToShare ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, textToShare)
            }
            startActivity(Intent.createChooser(shareIntent, "Yout Support Link"))
          },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLinkIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshPermissions()
  }

  private fun handleDeepLinkIntent(intent: Intent?) {
    val uri = intent?.data ?: return
    val parsed = P2pTransportManager.parseInviteOrToken(uri.toString())
    if (parsed != null) {
      pendingDeepLinkInvite = parsed
    }
  }
}

@Composable
private fun YoutAppRoot(
  viewModel: SupportViewModel,
  deepLinkInvite: ParsedInvite?,
  onConsumeDeepLink: () -> Unit,
  onShareText: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val copy = copyFor(state.language)
  val context = androidx.compose.ui.platform.LocalContext.current
  val clipboard = LocalClipboardManager.current
  val snackbarHostState = remember { SnackbarHostState() }

  var showConnectDialog by remember { mutableStateOf(false) }
  var prefilledInvite by remember { mutableStateOf<ParsedInvite?>(null) }
  var showManualDialog by remember { mutableStateOf(false) }

  LaunchedEffect(deepLinkInvite) {
    if (deepLinkInvite != null) {
      prefilledInvite = deepLinkInvite
      showConnectDialog = true
      onConsumeDeepLink()
    }
  }

  LaunchedEffect(state.statusBannerError) {
    val err = state.statusBannerError
    if (!err.isNullOrBlank()) {
      snackbarHostState.showSnackbar(err)
      viewModel.clearErrorBanner()
    }
  }

  val screenCaptureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    val data = result.data
    if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
      YoutSupportService.startCapture(context, result.resultCode, data)
    }
  }

  val mediaPermissionsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    viewModel.refreshPermissions()
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    viewModel.refreshPermissions()
  }

  val toggleScreenShareAction: () -> Unit = {
    if (state.permissions.screenCaptureActive) {
      YoutSupportService.stopCapture(context)
    } else {
      val mpm = context.getSystemService(MediaProjectionManager::class.java)
      screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }
  }

  val requestMediaPermissionsAction: () -> Unit = {
    mediaPermissionsLauncher.launch(
      arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
    )
  }

  val requestNotificationPermissionAction: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val shareLinkAction: () -> Unit = {
    val link = viewModel.buildShareLink()
    val laptopUrl = viewModel.buildLaptopWebUrl()
    val message = if (state.language == AppLanguage.BANGLA) {
      "Yout সাপোর্ট কোড: ${state.temporaryCode}\nমোবাইল লিংক: $link\nল্যাপটপ ব্রাউজার লিংক: $laptopUrl"
    } else {
      "Yout Support Code: ${state.temporaryCode}\nPhone Link: $link\nLaptop Browser: $laptopUrl"
    }
    onShareText(message)
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 660.dp

    Scaffold(
      containerColor = MidnightBg,
      snackbarHost = {
        SnackbarHost(hostState = snackbarHostState) { data ->
          Snackbar(snackbarData = data)
        }
      },
      topBar = {
        YoutTopBar(
          state = state,
          copy = copy,
          onToggleLanguage = { viewModel.toggleLanguage() },
          onDisconnectClick = { viewModel.disconnectSession() },
        )
      },
      bottomBar = {
        if (!useRail) {
          YoutBottomNavBar(
            activeTab = state.activeTab,
            copy = copy,
            onSelectTab = { viewModel.selectTab(it) },
          )
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          YoutNavigationRail(
            activeTab = state.activeTab,
            copy = copy,
            onSelectTab = { viewModel.selectTab(it) },
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          TransparencyLiveBanner(
            state = state,
            copy = copy,
            onDisconnectClick = { viewModel.disconnectSession() },
          )
          Box(modifier = Modifier.weight(1f)) {
            when (state.activeTab) {
              MainTab.THIS_DEVICE -> ThisDeviceScreen(
                state = state,
                copy = copy,
                onShareLink = shareLinkAction,
                onOpenConnectDialog = {
                  prefilledInvite = null
                  showConnectDialog = true
                },
                onRegenerateCode = { viewModel.regenerateCode() },
                onCopyCode = {
                  clipboard.setText(AnnotatedString(state.temporaryCode))
                  Toast.makeText(context, copy.copiedToast, Toast.LENGTH_SHORT).show()
                },
                onCopyLaptopLink = {
                  clipboard.setText(AnnotatedString(viewModel.buildLaptopWebUrl()))
                  Toast.makeText(context, copy.copiedToast, Toast.LENGTH_SHORT).show()
                },
                onOpenManualSignal = { showManualDialog = true },
                onToggleScreenShare = toggleScreenShareAction,
                onRequestMediaPermissions = requestMediaPermissionsAction,
                onRequestNotificationPermission = requestNotificationPermissionAction,
              )

              MainTab.REMOTE_CONTROL -> RemoteControlScreen(
                state = state,
                copy = copy,
                onToggleScreenShare = toggleScreenShareAction,
                onSendNavCommand = { viewModel.sendNavCommand(it) },
                onSendRemoteTap = { x, y -> viewModel.sendRemoteTap(x, y) },
                onBrightnessChange = { viewModel.updateBrightness(it) },
                onToggleVoiceCall = {
                  if (!state.permissions.micGranted) {
                    requestMediaPermissionsAction()
                  } else {
                    viewModel.toggleVoiceCall()
                  }
                },
                onToggleCamera = {
                  if (!state.permissions.cameraGranted) {
                    requestMediaPermissionsAction()
                  } else {
                    viewModel.setCameraSharing(!state.capabilities.cameraSharing)
                  }
                },
                onFlipCamera = { viewModel.flipCameraFacing() },
                onLocalCameraFrame = { viewModel.onLocalCameraFrame(it) },
                onDisconnect = { viewModel.disconnectSession() },
              )

              MainTab.CHAT -> ChatScreen(
                state = state,
                copy = copy,
                onSendMessage = { viewModel.sendChatMessage(it) },
                onOpenManualSignal = { showManualDialog = true },
              )
            }
          }
        }
      }
    }
  }

  if (showConnectDialog) {
    ConnectCodeDialog(
      copy = copy,
      initialInvite = prefilledInvite,
      defaultHostIp = state.localIpAddress,
      onParseInvite = { viewModel.parseInvite(it) },
      onConnect = { ip, port, code -> viewModel.connectToPeer(ip, port, code) },
      onDismiss = { showConnectDialog = false },
    )
  }

  state.incomingConsent?.let { request ->
    IncomingConsentDialog(
      request = request,
      copy = copy,
      onAccept = { viewModel.respondToIncomingConsent(true) },
      onDecline = { viewModel.respondToIncomingConsent(false) },
    )
  }

  if (showManualDialog) {
    ManualSignalingDialog(
      copy = copy,
      deepLink = viewModel.buildShareLink(),
      manualToken = viewModel.buildManualToken(),
      onCopyText = { text ->
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, copy.copiedToast, Toast.LENGTH_SHORT).show()
      },
      onParseAndConnect = { payload ->
        val parsed = viewModel.parseInvite(payload)
        if (parsed != null) {
          viewModel.connectToPeer(parsed.hostIp, parsed.port, parsed.code)
          true
        } else {
          false
        }
      },
      onDismiss = { showManualDialog = false },
    )
  }
}
