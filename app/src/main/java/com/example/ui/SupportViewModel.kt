package com.example.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppLanguage
import com.example.model.MainTab
import com.example.model.RemoteNavCommand
import com.example.model.SupportUiState
import com.example.network.ParsedInvite
import com.example.network.P2pTransportManager
import com.example.session.SupportSessionController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.youtPrefs by preferencesDataStore(name = "yout_preferences")

class SupportViewModel(application: Application) : AndroidViewModel(application) {

  private val langKey = stringPreferencesKey("app_language")

  val uiState: StateFlow<SupportUiState> = SupportSessionController.uiState

  init {
    SupportSessionController.initialize(application)
    viewModelScope.launch {
      val prefs = getApplication<Application>().youtPrefs.data.first()
      val saved = prefs[langKey]
      if (saved == AppLanguage.ENGLISH.name) {
        SupportSessionController.setLanguage(AppLanguage.ENGLISH)
      } else {
        SupportSessionController.setLanguage(AppLanguage.BANGLA)
      }
    }
  }

  fun toggleLanguage() {
    val next = if (uiState.value.language == AppLanguage.BANGLA) {
      AppLanguage.ENGLISH
    } else {
      AppLanguage.BANGLA
    }
    SupportSessionController.setLanguage(next)
    viewModelScope.launch {
      getApplication<Application>().youtPrefs.edit { it[langKey] = next.name }
    }
  }

  fun selectTab(tab: MainTab) = SupportSessionController.selectTab(tab)

  fun refreshPermissions() = SupportSessionController.refreshPermissions(getApplication())

  fun regenerateCode() = SupportSessionController.regenerateTemporaryCode()

  fun buildShareLink(): String = SupportSessionController.buildShareLink()

  fun buildManualToken(): String = SupportSessionController.buildManualToken()

  fun connectToPeer(hostIp: String, port: Int, code: String) {
    SupportSessionController.connectToPeer(hostIp, port, code)
  }

  fun parseInvite(raw: String): ParsedInvite? = P2pTransportManager.parseInviteOrToken(raw)

  fun respondToIncomingConsent(accepted: Boolean) {
    SupportSessionController.respondToIncomingConsent(accepted)
  }

  fun disconnectSession() = SupportSessionController.disconnectSession("User disconnected")

  fun sendNavCommand(command: RemoteNavCommand) =
    SupportSessionController.sendNavigationCommand(command)

  fun sendRemoteTap(xNorm: Float, yNorm: Float) =
    SupportSessionController.sendRemoteTap(xNorm, yNorm)

  fun updateBrightness(percent: Int) = SupportSessionController.updateBrightness(percent)

  fun toggleVoiceCall(): Boolean = SupportSessionController.toggleVoiceCall()

  fun setCameraSharing(enabled: Boolean) = SupportSessionController.setCameraSharing(enabled)

  fun flipCameraFacing() = SupportSessionController.flipCameraFacing()

  fun onLocalCameraFrame(jpegBytes: ByteArray) =
    SupportSessionController.onLocalCameraFrameCaptured(jpegBytes)

  fun sendChatMessage(text: String) = SupportSessionController.sendChatMessage(text)

  fun clearErrorBanner() = SupportSessionController.clearErrorBanner()
}
