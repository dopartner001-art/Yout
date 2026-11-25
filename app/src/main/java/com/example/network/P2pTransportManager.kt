package com.example.network

import android.net.Uri
import android.util.Base64
import com.example.model.WirePacketType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

data class ParsedInvite(
  val code: String,
  val hostIp: String,
  val port: Int,
  val peerName: String = "",
)

interface TransportListener {
  fun onHostListening(ipAddress: String, port: Int)
  fun onIncomingConsent(peerName: String, code: String, peerIp: String): CompletableDeferred<Boolean>
  fun onConnected(isHost: Boolean, peerName: String, peerEndpoint: String)
  fun onDisconnected(reason: String)
  fun onPacketReceived(type: Byte, payload: ByteArray)
  fun onTransportError(message: String)
}

class P2pTransportManager(
  private val listener: TransportListener,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val writeMutex = Mutex()
  private val random = SecureRandom()

  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private var activeSocket: Socket? = null
  private var readJob: Job? = null
  private var outputStream: DataOutputStream? = null

  @Volatile
  var expectedCode: String = generateTemporaryCode()
    private set

  @Volatile
  var localDeviceName: String = "Yout Device"

  @Volatile
  var boundPort: Int = 8765
    private set

  fun regenerateCode(): String {
    expectedCode = generateTemporaryCode()
    return expectedCode
  }

  fun startHostServer(preferredPort: Int = 8765) {
    if (serverJob?.isActive == true && serverSocket?.isClosed == false) {
      listener.onHostListening(discoverLocalIpv4Address(), boundPort)
      return
    }
    serverJob = scope.launch {
      var opened: ServerSocket? = null
      for (candidate in preferredPort..(preferredPort + 10)) {
        try {
          opened = ServerSocket(candidate).apply { reuseAddress = true }
          boundPort = candidate
          break
        } catch (_: Exception) {
          // Try next port
        }
      }
      if (opened == null) {
        listener.onTransportError("Unable to open local P2P port")
        return@launch
      }
      serverSocket = opened
      listener.onHostListening(discoverLocalIpv4Address(), boundPort)

      while (isActive && !opened.isClosed) {
        try {
          val client = opened.accept()
          handleIncomingClient(client)
        } catch (_: Exception) {
          if (opened.isClosed) break
        }
      }
    }
  }

  private suspend fun handleIncomingClient(client: Socket) {
    try {
      client.soTimeout = 45000
      val input = DataInputStream(BufferedInputStream(client.getInputStream()))
      val output = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

      val packetType = input.readByte()
      val length = input.readInt()
      if (packetType != WirePacketType.HANDSHAKE_REQ || length <= 0 || length > 65536) {
        client.close()
        return
      }
      val payload = ByteArray(length)
      input.readFully(payload)
      val json = JSONObject(String(payload, Charsets.UTF_8))
      val submittedCode = normalizeCode(json.optString("code"))
      val peerName = json.optString("name", "Helper")
      val remoteIp = client.inetAddress.hostAddress ?: "Peer"

      if (submittedCode != normalizeCode(expectedCode)) {
        sendRawPacket(output, WirePacketType.HANDSHAKE_REJECT, "Invalid or expired temporary code".toByteArray())
        client.close()
        return
      }

      val deferred = listener.onIncomingConsent(peerName, expectedCode, remoteIp)
      val approved = withTimeoutOrNull(45_000L) { deferred.await() } ?: false
      if (!approved) {
        sendRawPacket(output, WirePacketType.HANDSHAKE_REJECT, "Session declined by host".toByteArray())
        client.close()
        return
      }

      closeActiveConnectionOnly()
      client.soTimeout = 0
      activeSocket = client
      outputStream = output

      val ackJson = JSONObject().put("name", localDeviceName).toString()
      sendRawPacket(output, WirePacketType.HANDSHAKE_ACK, ackJson.toByteArray(Charsets.UTF_8))
      listener.onConnected(isHost = true, peerName = peerName, peerEndpoint = "$remoteIp:${client.port}")
      startReaderLoop(client, input)
    } catch (e: Exception) {
      try {
        client.close()
      } catch (_: Exception) {
      }
      listener.onTransportError(e.message ?: "Incoming connection failed")
    }
  }

  fun connectToHost(hostIp: String, port: Int, code: String) {
    scope.launch {
      try {
        closeActiveConnectionOnly()
        val socket = Socket()
        socket.connect(InetSocketAddress(hostIp.trim(), port), 8000)
        socket.soTimeout = 45000

        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

        val reqJson = JSONObject()
          .put("code", normalizeCode(code))
          .put("name", localDeviceName)
          .toString()
        sendRawPacket(output, WirePacketType.HANDSHAKE_REQ, reqJson.toByteArray(Charsets.UTF_8))

        val respType = input.readByte()
        val respLen = input.readInt()
        val respPayload = if (respLen in 1..65536) {
          ByteArray(respLen).also { input.readFully(it) }
        } else {
          ByteArray(0)
        }

        if (respType != WirePacketType.HANDSHAKE_ACK) {
          val reason = if (respPayload.isNotEmpty()) String(respPayload, Charsets.UTF_8) else "Connection declined"
          socket.close()
          listener.onDisconnected(reason)
          return@launch
        }

        socket.soTimeout = 0
        activeSocket = socket
        outputStream = output
        val ackObj = runCatching { JSONObject(String(respPayload, Charsets.UTF_8)) }.getOrNull()
        val hostName = ackObj?.optString("name")?.ifBlank { "Host" } ?: "Host"
        listener.onConnected(isHost = false, peerName = hostName, peerEndpoint = "$hostIp:$port")
        startReaderLoop(socket, input)
      } catch (e: Exception) {
        listener.onDisconnected(e.message ?: "Unable to connect to host")
      }
    }
  }

  private fun startReaderLoop(socket: Socket, input: DataInputStream) {
    readJob?.cancel()
    readJob = scope.launch {
      try {
        while (isActive && !socket.isClosed) {
          val type = input.readByte()
          val length = input.readInt()
          if (length < 0 || length > 8_000_000) break
          val payload = ByteArray(length)
          if (length > 0) {
            input.readFully(payload)
          }
          if (type == WirePacketType.DISCONNECT) {
            val msg = if (payload.isNotEmpty()) String(payload, Charsets.UTF_8) else "Disconnected by peer"
            closeActiveConnectionOnly()
            listener.onDisconnected(msg)
            return@launch
          }
          listener.onPacketReceived(type, payload)
        }
      } catch (_: Exception) {
        if (activeSocket == socket) {
          closeActiveConnectionOnly()
          listener.onDisconnected("Connection closed")
        }
      }
    }
  }

  fun sendPacket(type: Byte, payload: ByteArray) {
    val out = outputStream ?: return
    scope.launch {
      try {
        writeMutex.withLock {
          sendRawPacket(out, type, payload)
        }
      } catch (_: Exception) {
        closeActiveConnectionOnly()
        listener.onDisconnected("Connection lost")
      }
    }
  }

  private fun sendRawPacket(output: DataOutputStream, type: Byte, payload: ByteArray) {
    output.writeByte(type.toInt())
    output.writeInt(payload.size)
    if (payload.isNotEmpty()) {
      output.write(payload)
    }
    output.flush()
  }

  fun disconnect(reason: String = "Disconnected") {
    val out = outputStream
    scope.launch {
      if (out != null) {
        runCatching {
          writeMutex.withLock {
            sendRawPacket(out, WirePacketType.DISCONNECT, reason.toByteArray(Charsets.UTF_8))
          }
        }
      }
      closeActiveConnectionOnly()
      listener.onDisconnected(reason)
    }
  }

  private fun closeActiveConnectionOnly() {
    readJob?.cancel()
    readJob = null
    runCatching { activeSocket?.close() }
    activeSocket = null
    outputStream = null
  }

  fun shutdown() {
    closeActiveConnectionOnly()
    serverJob?.cancel()
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  fun buildDeepLink(ip: String = discoverLocalIpv4Address()): String {
    return "yout://connect?code=${Uri.encode(expectedCode)}&host=${Uri.encode("$ip:$boundPort")}&name=${Uri.encode(localDeviceName)}"
  }

  fun buildManualToken(ip: String = discoverLocalIpv4Address()): String {
    val json = JSONObject()
      .put("code", expectedCode)
      .put("ip", ip)
      .put("port", boundPort)
      .put("name", localDeviceName)
      .toString()
    val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return "YOUT1:$encoded"
  }

  fun discoverLocalIpv4Address(): String {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
      var fallback: String? = null
      while (interfaces.hasMoreElements()) {
        val ni = interfaces.nextElement()
        if (!ni.isUp || ni.isLoopback) continue
        val addrs = ni.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (addr is Inet4Address && !addr.isLoopbackAddress) {
            val host = addr.hostAddress ?: continue
            if (ni.name.contains("wlan", ignoreCase = true) || ni.name.contains("ap", ignoreCase = true)) {
              return host
            }
            if (fallback == null) fallback = host
          }
        }
      }
      return fallback ?: "127.0.0.1"
    } catch (_: Exception) {
      return "127.0.0.1"
    }
  }

  private fun generateTemporaryCode(): String {
    val part1 = 100 + random.nextInt(900)
    val part2 = 100 + random.nextInt(900)
    val part3 = 100 + random.nextInt(900)
    return "$part1-$part2-$part3"
  }

  companion object {
    fun normalizeCode(raw: String): String = raw.filter { it.isDigit() }

    fun formatNineDigitCode(raw: String): String {
      val digits = normalizeCode(raw).take(9)
      return when {
        digits.length <= 3 -> digits
        digits.length <= 6 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
        else -> "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
      }
    }

    fun parseInviteOrToken(rawInput: String): ParsedInvite? {
      val trimmed = rawInput.trim()
      if (trimmed.isEmpty()) return null

      if (trimmed.startsWith("YOUT1:", ignoreCase = true)) {
        return runCatching {
          val b64 = trimmed.substringAfter(":")
          val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
          val obj = JSONObject(decoded)
          ParsedInvite(
            code = formatNineDigitCode(obj.getString("code")),
            hostIp = obj.getString("ip"),
            port = obj.optInt("port", 8765),
            peerName = obj.optString("name", ""),
          )
        }.getOrNull()
      }

      if (trimmed.startsWith("yout://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return runCatching {
          val uri = Uri.parse(trimmed)
          val code = uri.getQueryParameter("code").orEmpty()
          val hostParam = uri.getQueryParameter("host").orEmpty()
          val parts = hostParam.split(":")
          val ip = parts.getOrNull(0).orEmpty()
          val port = parts.getOrNull(1)?.toIntOrNull() ?: 8765
          if (code.isBlank() || ip.isBlank()) null
          else ParsedInvite(
            code = formatNineDigitCode(code),
            hostIp = ip,
            port = port,
            peerName = uri.getQueryParameter("name").orEmpty(),
          )
        }.getOrNull()
      }

      return null
    }
  }
}
