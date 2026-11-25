package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.i18n.AppCopy
import com.example.model.SupportUiState
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardElevated
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateTextMuted

@Composable
fun ChatScreen(
  state: SupportUiState,
  copy: AppCopy,
  onSendMessage: (String) -> Unit,
  onOpenManualSignal: () -> Unit,
) {
  var draft by remember { mutableStateOf("") }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      OutlinedButton(
        onClick = onOpenManualSignal,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MidnightOutline),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("chat_manual_signal_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.QrCode2,
          contentDescription = null,
          tint = CyanGlow,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = copy.manualSignalBtn,
          color = PureWhite,
          style = MaterialTheme.typography.labelLarge,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (state.chatMessages.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Chat,
              contentDescription = null,
              tint = CyanGlow,
              modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = copy.chatEmptyTitle,
              style = MaterialTheme.typography.titleMedium,
              color = PureWhite,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = copy.chatEmptyDesc,
              style = MaterialTheme.typography.bodySmall,
              color = SlateTextMuted,
              textAlign = TextAlign.Center,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.chatMessages, key = { it.id }) { msg ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (msg.isLocal) Arrangement.End else Arrangement.Start,
            ) {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (msg.isLocal) ElectricBlue else MidnightCardElevated,
                border = if (msg.isLocal) null else BorderStroke(1.dp, MidnightOutline),
                modifier = Modifier.widthIn(max = 280.dp),
              ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                  Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite,
                  )
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = "${msg.senderName} • ${msg.timeText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite.copy(alpha = 0.72f),
                  )
                }
              }
            }
          }
        }
      }

      // Quick support chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        copy.quickReplies.forEach { preset ->
          Surface(
            shape = RoundedCornerShape(50),
            color = MidnightCard,
            border = BorderStroke(1.dp, MidnightOutline),
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .clickable { onSendMessage(preset) },
          ) {
            Text(
              text = preset,
              style = MaterialTheme.typography.labelMedium,
              color = CyanGlow,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          placeholder = {
            Text(copy.chatInputHint, color = SlateTextMuted)
          },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedContainerColor = MidnightCard,
            unfocusedContainerColor = MidnightCard,
            focusedBorderColor = CyanGlow,
            unfocusedBorderColor = MidnightOutline,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("chat_input_field"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
          onClick = {
            if (draft.isNotBlank()) {
              onSendMessage(draft)
              draft = ""
            }
          },
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = ElectricBlue,
            contentColor = PureWhite,
          ),
          modifier = Modifier
            .size(50.dp)
            .testTag("chat_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = copy.sendBtn,
          )
        }
      }
    }
  }
}
