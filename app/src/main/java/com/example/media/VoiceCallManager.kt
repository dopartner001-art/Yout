package com.example.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VoiceCallManager(
  private val context: Context,
  private val onAudioChunkCaptured: (ByteArray) -> Unit,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var captureJob: Job? = null
  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null

  @Volatile
  var isRunning: Boolean = false
    private set

  fun start(): Boolean {
    if (isRunning) return true
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    if (!hasMic) return false

    val sampleRate = 16000
    val minRecBuf = AudioRecord.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(2048)

    val minPlayBuf = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(2048)

    return try {
      val recorder = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minRecBuf * 2,
      )
      if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        return false
      }

      val track = AudioTrack(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
        AudioFormat.Builder()
          .setSampleRate(sampleRate)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build(),
        minPlayBuf * 2,
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE,
      )

      audioRecord = recorder
      audioTrack = track
      recorder.startRecording()
      track.play()
      isRunning = true

      captureJob = scope.launch {
        val buffer = ByteArray(1280)
        while (isActive && isRunning) {
          val read = recorder.read(buffer, 0, buffer.size)
          if (read > 0) {
            onAudioChunkCaptured(buffer.copyOf(read))
          }
        }
      }
      true
    } catch (_: Exception) {
      stop()
      false
    }
  }

  fun playIncomingChunk(pcmBytes: ByteArray) {
    if (!isRunning) return
    val track = audioTrack ?: return
    runCatching {
      track.write(pcmBytes, 0, pcmBytes.size)
    }
  }

  fun stop() {
    isRunning = false
    captureJob?.cancel()
    captureJob = null
    runCatching {
      audioRecord?.stop()
      audioRecord?.release()
    }
    audioRecord = null
    runCatching {
      audioTrack?.stop()
      audioTrack?.release()
    }
    audioTrack = null
  }
}
