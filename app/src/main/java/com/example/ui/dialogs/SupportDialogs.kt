package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.AppCopy
import com.example.model.IncomingConsentRequest
import com.example.network.ParsedInvite
import com.example.network.P2pTransportManager
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardElevated
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateTextMuted

@Composable
fun ConnectCodeDialog(
  copy: AppCopy,
  initialInvite: ParsedInvite?,
  defaultHostIp: String,
  onParseInvite: (String) -> ParsedInvite?,
  onConnect: (hostIp: String, port: Int, code: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var codeText by remember(initialInvite) {
    mutableStateOf(initialInvite?.code ?: "")
  }
  var hostText by remember(initialInvite) {
    mutableStateOf(
      if (initialInvite != null) "${initialInvite.hostIp}:${initialInvite.port}" else "$defaultHostIp:8765",
    )
  }
  var tokenInput by remember { mutableStateOf("") }
  val clipboard = LocalClipboardManager.current

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MidnightCard,
    titleContentColor = PureWhite,
    textContentColor = PureWhite,
    icon = {
      Icon(
        imageVector = Icons.Rounded.Link,
        contentDescription = null,
        tint = CyanGlow,
        modifier = Modifier.size(30.dp),
      )
    },
    title = {
      Text(
        text = copy.connectDialogTitle,
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = tokenInput,
          onValueChange = { value ->
            tokenInput = value
            onParseInvite(value)?.let { parsed ->
              codeText = parsed.code
              hostText = "${parsed.hostIp}:${parsed.port}"
            }
          },
          label = { Text(copy.connectDialogHintToken, style = MaterialTheme.typography.bodySmall) },
          trailingIcon = {
            TextButton(onClick = {
              val clip = clipboard.getText()?.text.orEmpty()
              tokenInput = clip
              onParseInvite(clip)?.let { parsed ->
                codeText = parsed.code
                hostText = "${parsed.hostIp}:${parsed.port}"
              }
            }) {
              Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = CyanGlow)
            }
          },
          singleLine = true,
          colors = dialogTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_token_input"),
        )

        OutlinedTextField(
          value = codeText,
          onValueChange = { raw ->
            codeText = P2pTransportManager.formatNineDigitCode(raw)
          },
          label = { Text(copy.connectDialogHintCode, style = MaterialTheme.typography.bodySmall) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          colors = dialogTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_code_input"),
        )

        OutlinedTextField(
          value = hostText,
          onValueChange = { hostText = it },
          label = { Text(copy.connectDialogHintIp, style = MaterialTheme.typography.bodySmall) },
          singleLine = true,
          colors = dialogTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_host_input"),
        )
      }
    },
    confirmButton = {
      val digitsOk = P2pTransportManager.normalizeCode(codeText).length == 9
      val hostParts = hostText.trim().split(":")
      val ip = hostParts.getOrNull(0).orEmpty()
      val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8765
      Button(
        onClick = {
          onConnect(ip, port, codeText)
          onDismiss()
        },
        enabled = digitsOk && ip.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = PureWhite),
        modifier = Modifier.testTag("confirm_connect_button"),
      ) {
        Text(copy.connectAction)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(copy.cancelAction, color = SlateTextMuted)
      }
    },
  )
}

@Composable
fun IncomingConsentDialog(
  request: IncomingConsentRequest,
  copy: AppCopy,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDecline,
    containerColor = MidnightCard,
    titleContentColor = PureWhite,
    textContentColor = PureWhite,
    icon = {
      Icon(
        imageVector = Icons.Rounded.VerifiedUser,
        contentDescription = null,
        tint = EmeraldLive,
        modifier = Modifier.size(34.dp),
      )
    },
    title = {
      Text(text = copy.consentDialogTitle, style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MidnightCardElevated,
          border = BorderStroke(1.dp, MidnightOutline),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
              text = request.peerName,
              style = MaterialTheme.typography.titleMedium,
              color = PureWhite,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "${request.peerAddress}  •  ${request.sessionCode}",
              fontFamily = JetBrainsMonoFontFamily,
              style = MaterialTheme.typography.bodySmall,
              color = CyanGlow,
            )
          }
        }
        Text(
          text = copy.consentDialogBody,
          style = MaterialTheme.typography.bodyMedium,
          color = SlateTextMuted,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onAccept,
        colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive, contentColor = MidnightCard),
        modifier = Modifier.testTag("consent_accept_button"),
      ) {
        Text(copy.consentAccept, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDecline,
        border = BorderStroke(1.dp, CoralAlert),
        modifier = Modifier.testTag("consent_decline_button"),
      ) {
        Text(copy.consentDecline, color = CoralAlert)
      }
    },
  )
}

@Composable
fun ManualSignalingDialog(
  copy: AppCopy,
  deepLink: String,
  manualToken: String,
  onCopyText: (String) -> Unit,
  onParseAndConnect: (String) -> Boolean,
  onDismiss: () -> Unit,
) {
  var pastedPayload by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MidnightCard,
    titleContentColor = PureWhite,
    textContentColor = PureWhite,
    icon = {
      Icon(
        imageVector = Icons.Rounded.Security,
        contentDescription = null,
        tint = CyanGlow,
        modifier = Modifier.size(28.dp),
      )
    },
    title = {
      Text(text = copy.manualDialogTitle, style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = copy.manualDialogDesc,
          style = MaterialTheme.typography.bodySmall,
          color = SlateTextMuted,
        )
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MidnightCardElevated,
          border = BorderStroke(1.dp, MidnightOutline),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
              text = deepLink,
              fontFamily = JetBrainsMonoFontFamily,
              style = MaterialTheme.typography.bodySmall,
              color = CyanGlow,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = manualToken,
              fontFamily = JetBrainsMonoFontFamily,
              style = MaterialTheme.typography.bodySmall,
              color = PureWhite,
              maxLines = 2,
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = { onCopyText(manualToken) },
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = PureWhite),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(copy.copyTokenBtn, style = MaterialTheme.typography.labelMedium)
          }
        }

        OutlinedTextField(
          value = pastedPayload,
          onValueChange = { pastedPayload = it },
          label = { Text(copy.connectDialogHintToken, style = MaterialTheme.typography.bodySmall) },
          singleLine = true,
          colors = dialogTextFieldColors(),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (onParseAndConnect(pastedPayload)) {
            onDismiss()
          }
        },
        enabled = pastedPayload.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive, contentColor = MidnightCard),
      ) {
        Text(copy.pasteConnectBtn)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(copy.cancelAction, color = SlateTextMuted)
      }
    },
  )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = PureWhite,
  unfocusedTextColor = PureWhite,
  focusedBorderColor = CyanGlow,
  unfocusedBorderColor = MidnightOutline,
  focusedLabelColor = CyanGlow,
  unfocusedLabelColor = SlateTextMuted,
  cursorColor = CyanGlow,
)
