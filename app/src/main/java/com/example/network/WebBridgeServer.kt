package com.example.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

interface WebBridgeListener {
  fun getExpectedSessionCode(): String
  fun getHostDeviceName(): String
  fun getLocalHostIp(): String
  fun getLatestScreenFrame(): ByteArray?
  fun getLatestCameraFrame(): ByteArray?
  fun getChatHistoryJson(): JSONArray
  fun isSessionConnected(): Boolean
  fun onWebIncomingConsent(peerName: String, code: String, peerIp: String): CompletableDeferred<Boolean>
  fun onWebConnected(peerName: String, peerEndpoint: String)
  fun onWebTapPointer(xNorm: Float, yNorm: Float)
  fun onWebNavPrompt(command: String)
  fun onWebChatMessage(sender: String, text: String)
  fun onLaptopScreenFrame(jpegBytes: ByteArray)
  fun onWebDisconnected(reason: String)
}

class WebBridgeServer(
  private val listener: WebBridgeListener,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null
  private val pendingLaptopCommands = ConcurrentLinkedQueue<JSONObject>()

  @Volatile
  var boundPort: Int = 8766
    private set

  @Volatile
  private var activeWebToken: String? = null

  fun hasActiveWebSession(): Boolean = activeWebToken != null

  fun queueLaptopCommand(cmdObj: JSONObject) {
    if (activeWebToken != null) {
      pendingLaptopCommands.add(cmdObj)
    }
  }

  fun start(preferredPort: Int = 8766) {
    if (acceptJob?.isActive == true && serverSocket?.isClosed == false) return
    acceptJob = scope.launch {
      var opened: ServerSocket? = null
      for (candidate in preferredPort..(preferredPort + 10)) {
        try {
          opened = ServerSocket(candidate).apply { reuseAddress = true }
          boundPort = candidate
          break
        } catch (_: Exception) {
        }
      }
      if (opened == null) return@launch
      serverSocket = opened

      while (isActive && !opened.isClosed) {
        try {
          val client = opened.accept()
          scope.launch { handleHttpClient(client) }
        } catch (_: Exception) {
          if (opened.isClosed) break
        }
      }
    }
  }

  fun clearActiveWebSession() {
    activeWebToken = null
    pendingLaptopCommands.clear()
  }

  fun shutdown() {
    activeWebToken = null
    pendingLaptopCommands.clear()
    acceptJob?.cancel()
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  private suspend fun handleHttpClient(socket: Socket) {
    try {
      socket.soTimeout = 15000
      val input = BufferedInputStream(socket.getInputStream())
      val output = socket.getOutputStream()

      val requestLine = readLine(input) ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0].uppercase()
      val fullPath = parts[1]
      val path = fullPath.substringBefore("?")
      val query = parseQuery(fullPath.substringAfter("?", ""))

      var contentLength = 0
      var hostHeader = ""
      while (true) {
        val header = readLine(input) ?: break
        if (header.isEmpty()) break
        if (header.startsWith("Content-Length:", ignoreCase = true)) {
          contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
        } else if (header.startsWith("Host:", ignoreCase = true)) {
          hostHeader = header.substringAfter(":").trim()
        }
      }

      val maxBody = 4_000_000
      val bodyBytes = if (contentLength in 1..maxBody) {
        ByteArray(contentLength).also { buf ->
          var readTotal = 0
          while (readTotal < contentLength) {
            val r = input.read(buf, readTotal, contentLength - readTotal)
            if (r <= 0) break
            readTotal += r
          }
        }
      } else {
        ByteArray(0)
      }
      val bodyStr = if (path != "/api/laptop-frame") String(bodyBytes, Charsets.UTF_8) else ""

      when {
        method == "GET" && path == "/" -> {
          val codePrefill = query["code"].orEmpty()
          writeResponse(
            output,
            "200 OK",
            "text/html; charset=utf-8",
            buildWebViewerHtml(codePrefill).toByteArray(Charsets.UTF_8),
          )
        }
        method == "POST" && path == "/api/connect" -> {
          val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
          val submittedCode = P2pTransportManager.normalizeCode(obj?.optString("code").orEmpty())
          val peerName = obj?.optString("name", "Laptop Browser").orEmpty().ifBlank { "Laptop Browser" }
          val expected = P2pTransportManager.normalizeCode(listener.getExpectedSessionCode())
          if (submittedCode != expected) {
            writeJson(output, "403 Forbidden", JSONObject().put("ok", false).put("error", "Invalid temporary code"))
            return
          }
          val peerIp = socket.inetAddress.hostAddress ?: "Browser"
          val deferred = listener.onWebIncomingConsent(peerName, listener.getExpectedSessionCode(), peerIp)
          val approved = withTimeoutOrNull(45_000L) { deferred.await() } ?: false
          if (!approved) {
            writeJson(output, "403 Forbidden", JSONObject().put("ok", false).put("error", "Declined by phone user"))
            return
          }
          val token = UUID.randomUUID().toString()
          activeWebToken = token
          pendingLaptopCommands.clear()
          listener.onWebConnected(peerName, "$peerIp (Web)")
          writeJson(
            output,
            "200 OK",
            JSONObject()
              .put("ok", true)
              .put("token", token)
              .put("hostName", listener.getHostDeviceName()),
          )
        }
        method == "GET" && path == "/api/frame" -> {
          val token = query["token"].orEmpty()
          if (activeWebToken == null || token != activeWebToken) {
            writeResponse(output, "401 Unauthorized", "text/plain", ByteArray(0))
            return
          }
          val frame = listener.getLatestScreenFrame()
          if (frame == null || frame.isEmpty()) {
            writeResponse(output, "204 No Content", "text/plain", ByteArray(0))
          } else {
            writeResponse(output, "200 OK", "image/jpeg", frame)
          }
        }
        method == "GET" && path == "/api/camframe" -> {
          val token = query["token"].orEmpty()
          if (activeWebToken == null || token != activeWebToken) {
            writeResponse(output, "401 Unauthorized", "text/plain", ByteArray(0))
            return
          }
          val frame = listener.getLatestCameraFrame()
          if (frame == null || frame.isEmpty()) {
            writeResponse(output, "204 No Content", "text/plain", ByteArray(0))
          } else {
            writeResponse(output, "200 OK", "image/jpeg", frame)
          }
        }
        method == "POST" && path == "/api/laptop-frame" -> {
          val token = query["token"].orEmpty()
          if (activeWebToken == null || token != activeWebToken) {
            writeJson(output, "401 Unauthorized", JSONObject().put("ok", false))
            return
          }
          if (bodyBytes.isNotEmpty()) {
            listener.onLaptopScreenFrame(bodyBytes)
          }
          writeJson(output, "200 OK", JSONObject().put("ok", true))
        }
        method == "GET" && path == "/api/laptop-commands" -> {
          val token = query["token"].orEmpty()
          if (activeWebToken == null || token != activeWebToken || !listener.isSessionConnected()) {
            writeJson(output, "401 Unauthorized", JSONObject().put("stop", true))
            return
          }
          val cmds = JSONArray()
          while (true) {
            val next = pendingLaptopCommands.poll() ?: break
            cmds.put(next)
          }
          writeJson(output, "200 OK", JSONObject().put("stop", false).put("commands", cmds))
        }
        method == "GET" && path == "/download/yout-laptop.bat" -> {
          val token = query["token"].orEmpty()
          if (activeWebToken == null || token != activeWebToken) {
            writeResponse(output, "401 Unauthorized", "text/plain", "Connect first".toByteArray())
            return
          }
          val baseHost = hostHeader.ifBlank { "${listener.getLocalHostIp()}:$boundPort" }
          val bat = buildLaptopLauncherBat(baseHost, token).toByteArray(Charsets.UTF_8)
          val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Content-Disposition: attachment; filename=\"Yout-Laptop.bat\"\r\n")
            append("Content-Length: ").append(bat.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
          }
          output.write(headers.toByteArray(Charsets.US_ASCII))
          output.write(bat)
          output.flush()
        }
        method == "GET" && path == "/api/laptop-agent.ps1" -> {
          val token = query["token"].orEmpty()
          if (activeWebToken == null || token != activeWebToken) {
            writeResponse(output, "401 Unauthorized", "text/plain", ByteArray(0))
            return
          }
          val baseHost = hostHeader.ifBlank { "${listener.getLocalHostIp()}:$boundPort" }
          val ps1 = buildLaptopPowerShellAgent(baseHost, token).toByteArray(Charsets.UTF_8)
          writeResponse(output, "200 OK", "text/plain; charset=utf-8", ps1)
        }
        method == "GET" && path == "/api/state" -> {
          val token = query["token"].orEmpty()
          val connected = activeWebToken != null && token == activeWebToken && listener.isSessionConnected()
          val json = JSONObject()
            .put("connected", connected)
            .put("hostName", listener.getHostDeviceName())
            .put("hasFrame", listener.getLatestScreenFrame() != null)
            .put("hasCamera", listener.getLatestCameraFrame() != null)
            .put("chat", if (connected) listener.getChatHistoryJson() else JSONArray())
          writeJson(output, "200 OK", json)
        }
        method == "POST" && path == "/api/tap" -> {
          val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
          val token = obj?.optString("token").orEmpty()
          if (activeWebToken != null && token == activeWebToken) {
            val x = obj?.optDouble("x", 0.5)?.toFloat() ?: 0.5f
            val y = obj?.optDouble("y", 0.5)?.toFloat() ?: 0.5f
            listener.onWebTapPointer(x, y)
            writeJson(output, "200 OK", JSONObject().put("ok", true))
          } else {
            writeJson(output, "401 Unauthorized", JSONObject().put("ok", false))
          }
        }
        method == "POST" && path == "/api/nav" -> {
          val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
          val token = obj?.optString("token").orEmpty()
          val cmd = obj?.optString("cmd").orEmpty()
          if (activeWebToken != null && token == activeWebToken && cmd.isNotBlank()) {
            listener.onWebNavPrompt(cmd)
            writeJson(output, "200 OK", JSONObject().put("ok", true))
          } else {
            writeJson(output, "401 Unauthorized", JSONObject().put("ok", false))
          }
        }
        method == "POST" && path == "/api/chat" -> {
          val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
          val token = obj?.optString("token").orEmpty()
          val text = obj?.optString("text").orEmpty()
          val sender = obj?.optString("name", "Laptop").orEmpty()
          if (activeWebToken != null && token == activeWebToken && text.isNotBlank()) {
            listener.onWebChatMessage(sender, text)
            writeJson(output, "200 OK", JSONObject().put("ok", true))
          } else {
            writeJson(output, "401 Unauthorized", JSONObject().put("ok", false))
          }
        }
        method == "POST" && path == "/api/disconnect" -> {
          val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
          val token = obj?.optString("token").orEmpty()
          if (activeWebToken != null && token == activeWebToken) {
            activeWebToken = null
            pendingLaptopCommands.clear()
            listener.onWebDisconnected("Disconnected from laptop browser")
          }
          writeJson(output, "200 OK", JSONObject().put("ok", true))
        }
        else -> {
          writeResponse(output, "404 Not Found", "text/plain", "Not Found".toByteArray())
        }
      }
    } catch (_: Exception) {
    } finally {
      runCatching { socket.close() }
    }
  }

  private fun readLine(input: BufferedInputStream): String? {
    val out = ByteArrayOutputStream()
    while (true) {
      val b = input.read()
      if (b == -1) return if (out.size() == 0) null else out.toString(Charsets.UTF_8.name())
      if (b == '\n'.code) break
      if (b != '\r'.code) out.write(b)
    }
    return out.toString(Charsets.UTF_8.name())
  }

  private fun parseQuery(qs: String): Map<String, String> {
    if (qs.isBlank()) return emptyMap()
    return qs.split("&").mapNotNull { pair ->
      val idx = pair.indexOf('=')
      if (idx <= 0) null
      else {
        val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
        val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        k to v
      }
    }.toMap()
  }

  private fun writeJson(output: OutputStream, status: String, json: JSONObject) {
    writeResponse(output, status, "application/json; charset=utf-8", json.toString().toByteArray(Charsets.UTF_8))
  }

  private fun writeResponse(output: OutputStream, status: String, contentType: String, body: ByteArray) {
    val headers = buildString {
      append("HTTP/1.1 ").append(status).append("\r\n")
      append("Content-Type: ").append(contentType).append("\r\n")
      append("Content-Length: ").append(body.size).append("\r\n")
      append("Cache-Control: no-store\r\n")
      append("Connection: close\r\n\r\n")
    }
    output.write(headers.toByteArray(Charsets.US_ASCII))
    if (body.isNotEmpty()) output.write(body)
    output.flush()
  }

  private fun buildLaptopLauncherBat(host: String, token: String): String = """
@echo off
title Yout Laptop Screen Share & Remote Control
echo ===================================================
echo  Yout Laptop Support Active (Transparent Mode)
echo  Phone Host: http://$host
echo  Close this window or press Ctrl+C anytime to stop.
echo ===================================================
curl -fsSL "http://$host/api/laptop-agent.ps1?token=$token" -o "%TEMP%\yout_agent.ps1"
if errorlevel 1 (
  echo Failed to connect to phone. Make sure both are on the same Wi-Fi/Hotspot.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%TEMP%\yout_agent.ps1"
pause
""".trimIndent()

  private fun buildLaptopPowerShellAgent(host: String, token: String): String = """
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class YoutWinInput {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
  public static void ClickAt(int x, int y) {
    SetCursorPos(x, y);
    mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
    mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
  }
}
"@
[YoutWinInput]::SetProcessDPIAware() | Out-Null

${'$'}base = "http://$host"
${'$'}tok = "$token"
${'$'}bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
${'$'}codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { ${'$'}_.MimeType -eq 'image/jpeg' } | Select-Object -First 1
${'$'}ep = New-Object System.Drawing.Imaging.EncoderParameters(1)
${'$'}ep.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, 55L)

Write-Host "Streaming Laptop Screen (${'$'}(${'$'}bounds.Width)x${'$'}(${'$'}bounds.Height)) to Phone... Press Ctrl+C to disconnect."

while (${'$'}true) {
  try {
    ${'$'}bmp = New-Object System.Drawing.Bitmap(${'$'}bounds.Width, ${'$'}bounds.Height)
    ${'$'}g = [System.Drawing.Graphics]::FromImage(${'$'}bmp)
    ${'$'}g.CopyFromScreen(${'$'}bounds.Location, [System.Drawing.Point]::Empty, ${'$'}bounds.Size)
    ${'$'}g.Dispose()

    ${'$'}targetW = [Math]::Min(1024, ${'$'}bounds.Width)
    ${'$'}targetH = [int](${'$'}bounds.Height * (${'$'}targetW / [double]${'$'}bounds.Width))
    ${'$'}scaled = New-Object System.Drawing.Bitmap(${'$'}bmp, ${'$'}targetW, ${'$'}targetH)
    ${'$'}bmp.Dispose()

    ${'$'}ms = New-Object System.IO.MemoryStream
    ${'$'}scaled.Save(${'$'}ms, ${'$'}codec, ${'$'}ep)
    ${'$'}scaled.Dispose()
    ${'$'}bytes = ${'$'}ms.ToArray()
    ${'$'}ms.Dispose()

    ${'$'}wc = New-Object System.Net.WebClient
    ${'$'}null = ${'$'}wc.UploadData("${'$'}base/api/laptop-frame?token=${'$'}tok", "POST", ${'$'}bytes)
    ${'$'}resp = ${'$'}wc.DownloadString("${'$'}base/api/laptop-commands?token=${'$'}tok") | ConvertFrom-Json
    ${'$'}wc.Dispose()

    if (${'$'}resp.stop -eq ${'$'}true) {
      Write-Host "Session disconnected by phone."
      break
    }

    foreach (${'$'}c in ${'$'}resp.commands) {
      if (${'$'}c.type -eq "tap") {
        ${'$'}px = [int](${'$'}c.x * ${'$'}bounds.Width)
        ${'$'}py = [int](${'$'}c.y * ${'$'}bounds.Height)
        [YoutWinInput]::ClickAt(${'$'}px, ${'$'}py)
      } elseif (${'$'}c.type -eq "nav") {
        if (${'$'}c.cmd -eq "BACK") { [System.Windows.Forms.SendKeys]::SendWait("%{LEFT}") }
        elseif (${'$'}c.cmd -eq "HOME") { [System.Windows.Forms.SendKeys]::SendWait("^{ESC}") }
        elseif (${'$'}c.cmd -eq "RECENTS") { [System.Windows.Forms.SendKeys]::SendWait("%{TAB}") }
        elseif (${'$'}c.cmd -eq "NOTIFICATIONS") { [System.Windows.Forms.SendKeys]::SendWait("^{ESC}") }
      }
    }
  } catch {
    Write-Host "Stopped or disconnected."
    break
  }
  Start-Sleep -Milliseconds 180
}
""".trimIndent()

  private fun buildWebViewerHtml(prefillCode: String): String {
    val safeCode = prefillCode.replace("\"", "")
    return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>Yout — Laptop &amp; Phone Bridge</title>
<style>
  body { margin:0; font-family:system-ui,-apple-system,sans-serif; background:#0A1124; color:#fff; }
  .wrap { max-width:1040px; margin:0 auto; padding:16px; }
  .top { display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
  .card { background:#172447; border:1px solid #2B3F70; border-radius:18px; padding:18px; margin-bottom:14px; }
  input, button, a.btn { font-size:14.5px; border-radius:12px; border:1px solid #2B3F70; padding:10px 14px; text-decoration:none; display:inline-block; }
  input { background:#0A1124; color:#fff; width:100%; box-sizing:border-box; }
  button, a.btn { cursor:pointer; font-weight:600; color:#fff; background:#2563EB; border:none; }
  button.danger { background:#EF4444; }
  button.emerald { background:#10B981; color:#0A1124; }
  button.sec { background:#1F305C; border:1px solid #2B3F70; }
  .grid { display:grid; grid-template-columns:1fr 340px; gap:14px; }
  @media (max-width:820px){ .grid{ grid-template-columns:1fr; } }
  .screen-box { position:relative; background:#050914; border-radius:14px; min-height:280px; display:flex; align-items:center; justify-content:center; overflow:hidden; border:1px solid #2B3F70; margin-bottom:10px; }
  .screen-box img { max-width:100%; max-height:66vh; cursor:crosshair; display:block; }
  .badge { display:inline-block; padding:5px 10px; border-radius:999px; background:#0E382B; color:#10B981; font-size:13px; font-weight:600; }
  .nav-row { display:flex; gap:8px; flex-wrap:wrap; margin-top:8px; }
  .chat-log { height:230px; overflow-y:auto; background:#0A1124; border-radius:12px; padding:10px; margin-bottom:10px; border:1px solid #2B3F70; }
  .msg { margin-bottom:8px; font-size:13.5px; }
  .muted { color:#94A3B8; font-size:13px; }
  .highlight-box { background:#0F294A; border:1px solid #06B6D4; border-radius:14px; padding:14px; margin-bottom:14px; }
</style>
</head>
<body>
<div class="wrap">
  <div class="top">
    <div>
      <h2 style="margin:0">Yout — Laptop &amp; Phone Bridge</h2>
      <div class="muted">Two-way Screen &amp; Camera • Phone controls Laptop • Disconnect anytime</div>
    </div>
    <div>
      <span id="statusBadge" class="badge" style="display:none">Connected</span>
      <button id="disconnectBtn" class="danger" style="display:none; margin-left:8px;" onclick="disconnectNow()">Disconnect</button>
    </div>
  </div>

  <div id="connectCard" class="card">
    <h3 style="margin-top:0">Connect with 9-Digit Temporary Code</h3>
    <p class="muted">Enter the temporary code shown on the phone. The phone owner will approve before anything starts.</p>
    <div style="display:flex; gap:10px; flex-wrap:wrap;">
      <input id="codeInput" value="$safeCode" placeholder="e.g. 469-752-988" style="flex:1; min-width:180px;" />
      <input id="nameInput" value="Laptop Browser" placeholder="Your name" style="flex:1; min-width:150px;" />
      <button onclick="connectSession()">Connect</button>
    </div>
    <p id="connectStatus" class="muted" style="margin-bottom:0;"></p>
  </div>

  <div id="sessionArea" style="display:none">
    <div class="highlight-box">
      <div style="display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap;">
        <div>
          <strong style="color:#06B6D4;">Show &amp; Control This Laptop from Your Phone</strong>
          <div id="laptopShareStatus" class="muted">Download &amp; run the 1-click Windows helper to see and control your entire laptop screen from your phone!</div>
        </div>
        <div style="display:flex; gap:8px; flex-wrap:wrap;">
          <a id="downloadAgentBtn" class="btn" style="background:#10B981; color:#0A1124;" href="#">Download Windows Helper (.bat)</a>
          <button onclick="copyRunCmd()">Copy Win+R Command</button>
          <button class="sec" onclick="startBrowserScreenShare()">Browser Share</button>
        </div>
      </div>
    </div>

    <div class="grid">
      <div>
        <div class="card">
          <strong style="display:block; margin-bottom:8px;">Phone Live Screen (Click to guide pointer)</strong>
          <div class="screen-box">
            <img id="screenImg" alt="Waiting for phone screen..." onclick="sendTap(event)" style="display:none" />
            <div id="screenPlaceholder" class="muted" style="padding:20px; text-align:center;">Waiting for phone user to tap "Start Screen Share" (or check Phone Camera below)...</div>
          </div>
          <div class="nav-row">
            <button class="sec" onclick="sendNav('BACK')">Guide: Back</button>
            <button class="sec" onclick="sendNav('HOME')">Guide: Home</button>
            <button class="sec" onclick="sendNav('RECENTS')">Guide: Recents</button>
            <button class="sec" onclick="sendNav('NOTIFICATIONS')">Guide: Notifications</button>
          </div>
        </div>

        <div class="card">
          <strong style="display:block; margin-bottom:8px;">Phone Live Camera</strong>
          <div class="screen-box" style="min-height:200px;">
            <img id="camImg" alt="Phone camera" style="display:none" />
            <div id="camPlaceholder" class="muted" style="padding:16px; text-align:center;">Phone camera is currently off.</div>
          </div>
        </div>
      </div>

      <div class="card">
        <h3 style="margin-top:0">Support Chat</h3>
        <div id="chatLog" class="chat-log"></div>
        <div style="display:flex; gap:8px;">
          <input id="chatInput" placeholder="Type message..." onkeydown="if(event.key==='Enter')sendChat()" />
          <button onclick="sendChat()">Send</button>
        </div>
      </div>
    </div>
  </div>
</div>

<video id="capVideo" autoplay playsinline style="display:none"></video>
<canvas id="capCanvas" style="display:none"></canvas>

<script>
let token = null;
let pollTimer = null;
let frameTimer = null;
let camTimer = null;
let browserShareTimer = null;
let browserStream = null;

async function connectSession() {
  const code = document.getElementById('codeInput').value;
  const name = document.getElementById('nameInput').value || 'Laptop Browser';
  const st = document.getElementById('connectStatus');
  st.textContent = 'Waiting for phone owner to approve...';
  try {
    const res = await fetch('/api/connect', {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify({code, name})
    });
    const data = await res.json();
    if (!data.ok) {
      st.textContent = data.error || 'Connection declined';
      return;
    }
    token = data.token;
    document.getElementById('downloadAgentBtn').href = '/download/yout-laptop.bat?token=' + encodeURIComponent(token);
    document.getElementById('connectCard').style.display = 'none';
    document.getElementById('sessionArea').style.display = 'block';
    document.getElementById('statusBadge').style.display = 'inline-block';
    document.getElementById('statusBadge').textContent = 'Connected to ' + (data.hostName || 'Phone');
    document.getElementById('disconnectBtn').style.display = 'inline-block';
    startLoops();
  } catch (e) {
    st.textContent = 'Failed to reach phone';
  }
}

function startLoops() {
  pollTimer = setInterval(async () => {
    if (!token) return;
    try {
      const r = await fetch('/api/state?token=' + encodeURIComponent(token));
      const s = await r.json();
      if (!s.connected) { resetUi('Session ended'); return; }
      if (!s.hasFrame) {
        document.getElementById('screenImg').style.display = 'none';
        document.getElementById('screenPlaceholder').style.display = 'block';
      }
      if (!s.hasCamera) {
        document.getElementById('camImg').style.display = 'none';
        document.getElementById('camPlaceholder').style.display = 'block';
      }
      const box = document.getElementById('chatLog');
      box.innerHTML = (s.chat || []).map(m => '<div class="msg"><b>'+m.sender+':</b> '+m.text+' <span class="muted">('+m.time+')</span></div>').join('');
      box.scrollTop = box.scrollHeight;
    } catch (_) {}
  }, 1400);

  frameTimer = setInterval(() => {
    if (!token) return;
    const img = document.getElementById('screenImg');
    const loader = new Image();
    loader.onload = () => {
      img.src = loader.src;
      img.style.display = 'block';
      document.getElementById('screenPlaceholder').style.display = 'none';
    };
    loader.src = '/api/frame?token=' + encodeURIComponent(token) + '&t=' + Date.now();
  }, 240);

  camTimer = setInterval(() => {
    if (!token) return;
    const img = document.getElementById('camImg');
    const loader = new Image();
    loader.onload = () => {
      img.src = loader.src;
      img.style.display = 'block';
      document.getElementById('camPlaceholder').style.display = 'none';
    };
    loader.src = '/api/camframe?token=' + encodeURIComponent(token) + '&t=' + Date.now();
  }, 260);
}

async function startBrowserScreenShare() {
  const st = document.getElementById('laptopShareStatus');
  if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) {
    st.textContent = 'Browser blocked screen capture on plain HTTP. Please click "Download Windows Control Helper (.bat)" and double-click it!';
    return;
  }
  try {
    browserStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
    const video = document.getElementById('capVideo');
    const canvas = document.getElementById('capCanvas');
    video.srcObject = browserStream;
    st.textContent = 'Streaming laptop screen to your phone from browser!';
    browserShareTimer = setInterval(() => {
      if (!token || !video.videoWidth) return;
      const w = Math.min(1024, video.videoWidth);
      const h = Math.round(video.videoHeight * (w / video.videoWidth));
      canvas.width = w;
      canvas.height = h;
      canvas.getContext('2d').drawImage(video, 0, 0, w, h);
      canvas.toBlob(blob => {
        if (blob && token) {
          fetch('/api/laptop-frame?token=' + encodeURIComponent(token), { method:'POST', body: blob }).catch(()=>{});
        }
      }, 'image/jpeg', 0.55);
    }, 220);
  } catch (err) {
    st.textContent = 'Use "Download Windows Helper (.bat)" or "Copy Win+R Command" to share and control your laptop from your phone.';
  }
}

function copyRunCmd() {
  if (!token) return;
  const url = location.origin + '/api/laptop-agent.ps1?token=' + encodeURIComponent(token);
  const cmd = 'powershell -NoProfile -ExecutionPolicy Bypass -Command "irm \'' + url + '\' | iex"';
  navigator.clipboard.writeText(cmd).then(() => {
    document.getElementById('laptopShareStatus').textContent = 'Copied! Press Win + R on your laptop, paste (Ctrl+V) and press Enter.';
  }).catch(() => {
    prompt('Copy this command and run in Win+R or PowerShell:', cmd);
  });
}

async function sendTap(ev) {
  if (!token) return;
  const rect = ev.target.getBoundingClientRect();
  const x = Math.max(0, Math.min(1, (ev.clientX - rect.left) / rect.width));
  const y = Math.max(0, Math.min(1, (ev.clientY - rect.top) / rect.height));
  await fetch('/api/tap', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token, x, y}) });
}

async function sendNav(cmd) {
  if (!token) return;
  await fetch('/api/nav', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token, cmd}) });
}

async function sendChat() {
  const inp = document.getElementById('chatInput');
  const text = inp.value.trim();
  if (!text || !token) return;
  inp.value = '';
  const name = document.getElementById('nameInput').value || 'Laptop';
  await fetch('/api/chat', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token, name, text}) });
}

async function disconnectNow() {
  if (token) {
    await fetch('/api/disconnect', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token}) }).catch(()=>{});
  }
  resetUi('Disconnected');
}

function resetUi(msg) {
  token = null;
  clearInterval(pollTimer);
  clearInterval(frameTimer);
  clearInterval(camTimer);
  clearInterval(browserShareTimer);
  if (browserStream) {
    browserStream.getTracks().forEach(t => t.stop());
    browserStream = null;
  }
  document.getElementById('connectCard').style.display = 'block';
  document.getElementById('sessionArea').style.display = 'none';
  document.getElementById('statusBadge').style.display = 'none';
  document.getElementById('disconnectBtn').style.display = 'none';
  document.getElementById('connectStatus').textContent = msg;
}
</script>
</body>
</html>
""".trimIndent()
  }
}
