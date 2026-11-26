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

interface WebBridgeListener {
  fun getExpectedSessionCode(): String
  fun getHostDeviceName(): String
  fun getLatestScreenFrame(): ByteArray?
  fun getChatHistoryJson(): JSONArray
  fun isSessionConnected(): Boolean
  fun onWebIncomingConsent(peerName: String, code: String, peerIp: String): CompletableDeferred<Boolean>
  fun onWebConnected(peerName: String, peerEndpoint: String)
  fun onWebTapPointer(xNorm: Float, yNorm: Float)
  fun onWebNavPrompt(command: String)
  fun onWebChatMessage(sender: String, text: String)
  fun onWebDisconnected(reason: String)
}

class WebBridgeServer(
  private val listener: WebBridgeListener,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null

  @Volatile
  var boundPort: Int = 8766
    private set

  @Volatile
  private var activeWebToken: String? = null

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
  }

  fun shutdown() {
    activeWebToken = null
    acceptJob?.cancel()
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  private suspend fun handleHttpClient(socket: Socket) {
    try {
      socket.soTimeout = 10000
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
      while (true) {
        val header = readLine(input) ?: break
        if (header.isEmpty()) break
        if (header.startsWith("Content-Length:", ignoreCase = true)) {
          contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
        }
      }

      val bodyBytes = if (contentLength in 1..65536) {
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
      val bodyStr = String(bodyBytes, Charsets.UTF_8)

      when {
        method == "GET" && path == "/" -> {
          val codePrefill = query["code"].orEmpty()
          writeResponse(output, "200 OK", "text/html; charset=utf-8", buildWebViewerHtml(codePrefill).toByteArray(Charsets.UTF_8))
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
        method == "GET" && path == "/api/state" -> {
          val token = query["token"].orEmpty()
          val connected = activeWebToken != null && token == activeWebToken && listener.isSessionConnected()
          val json = JSONObject()
            .put("connected", connected)
            .put("hostName", listener.getHostDeviceName())
            .put("hasFrame", listener.getLatestScreenFrame() != null)
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

  private fun buildWebViewerHtml(prefillCode: String): String {
    val safeCode = prefillCode.replace("\"", "")
    return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>Yout — Web Remote Support</title>
<style>
  body { margin:0; font-family:system-ui,-apple-system,sans-serif; background:#0A1124; color:#fff; }
  .wrap { max-width:960px; margin:0 auto; padding:16px; }
  .top { display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
  .card { background:#172447; border:1px solid #2B3F70; border-radius:18px; padding:18px; margin-bottom:14px; }
  input, button { font-size:15px; border-radius:12px; border:1px solid #2B3F70; padding:11px 14px; }
  input { background:#0A1124; color:#fff; width:100%; box-sizing:border-box; }
  button { cursor:pointer; font-weight:600; color:#fff; background:#2563EB; border:none; }
  button.danger { background:#EF4444; }
  button.sec { background:#1F305C; border:1px solid #2B3F70; }
  .grid { display:grid; grid-template-columns:1fr 320px; gap:14px; }
  @media (max-width:760px){ .grid{ grid-template-columns:1fr; } }
  .screen-box { position:relative; background:#050914; border-radius:14px; min-height:420px; display:flex; align-items:center; justify-content:center; overflow:hidden; border:1px solid #2B3F70; }
  .screen-box img { max-width:100%; max-height:78vh; cursor:crosshair; display:block; }
  .badge { display:inline-block; padding:5px 10px; border-radius:999px; background:#0E382B; color:#10B981; font-size:13px; font-weight:600; }
  .nav-row { display:flex; gap:8px; flex-wrap:wrap; margin-top:10px; }
  .chat-log { height:260px; overflow-y:auto; background:#0A1124; border-radius:12px; padding:10px; margin-bottom:10px; border:1px solid #2B3F70; }
  .msg { margin-bottom:8px; font-size:13.5px; }
  .muted { color:#94A3B8; font-size:13px; }
</style>
</head>
<body>
<div class="wrap">
  <div class="top">
    <div>
      <h2 style="margin:0">Yout — Laptop / Web Viewer</h2>
      <div class="muted">Transparent support • Both devices stay informed • Disconnect anytime</div>
    </div>
    <div id="statusBadge" class="badge" style="display:none">Connected</div>
    <button id="disconnectBtn" class="danger" style="display:none" onclick="disconnectNow()">Disconnect</button>
  </div>

  <div id="connectCard" class="card">
    <h3 style="margin-top:0">Connect with 9-Digit Temporary Code</h3>
    <p class="muted">Enter the temporary code shown on the phone. The phone owner will see an explicit Allow/Decline prompt.</p>
    <div style="display:flex; gap:10px; flex-wrap:wrap;">
      <input id="codeInput" value="$safeCode" placeholder="e.g. 469-752-988" style="flex:1; min-width:180px;" />
      <input id="nameInput" value="Laptop Browser" placeholder="Your name" style="flex:1; min-width:150px;" />
      <button onclick="connectSession()">Connect</button>
    </div>
    <p id="connectStatus" class="muted" style="margin-bottom:0;"></p>
  </div>

  <div id="sessionArea" class="grid" style="display:none">
    <div class="card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
        <strong>Live Phone Screen (Click to guide pointer)</strong>
      </div>
      <div class="screen-box">
        <img id="screenImg" alt="Waiting for phone to start screen share..." onclick="sendTap(event)" style="display:none" />
        <div id="screenPlaceholder" class="muted" style="padding:20px; text-align:center;">Waiting for phone user to tap "Start Screen Share"...</div>
      </div>
      <div class="nav-row">
        <button class="sec" onclick="sendNav('BACK')">Guide: Back</button>
        <button class="sec" onclick="sendNav('HOME')">Guide: Home</button>
        <button class="sec" onclick="sendNav('RECENTS')">Guide: Recents</button>
        <button class="sec" onclick="sendNav('NOTIFICATIONS')">Guide: Notifications</button>
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

<script>
let token = null;
let pollTimer = null;
let frameTimer = null;

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
    document.getElementById('connectCard').style.display = 'none';
    document.getElementById('sessionArea').style.display = 'grid';
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
      const box = document.getElementById('chatLog');
      box.innerHTML = (s.chat || []).map(m => '<div class="msg"><b>'+m.sender+':</b> '+m.text+' <span class="muted">('+m.time+')</span></div>').join('');
      box.scrollTop = box.scrollHeight;
    } catch (_) {}
  }, 1500);

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
  }, 220);
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
