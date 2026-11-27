package com.mmbazaar.user.ui

import android.os.Handler
import android.os.Looper
import com.mmbazaar.user.data.Supabase
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Supabase Realtime Phoenix channel.
 * Uses Phoenix heartbeat frames (not only WebSocket-level ping) and reconnects
 * while the owning Activity remains resumed.
 */
class StoreRealtime(private val api: Supabase, private val onChange: () -> Unit) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    @Volatile private var closed = false
    @Volatile private var connected = false
    private var ref = 0
    private var retrySeconds = 2L

    private val heartbeat = object : Runnable {
        override fun run() {
            val ws = socket
            if (!closed && connected && ws != null) {
                val heartbeatRef = nextRef()
                ws.send(JSONObject()
                    .put("topic", "phoenix")
                    .put("event", "heartbeat")
                    .put("payload", JSONObject())
                    .put("ref", heartbeatRef)
                    .toString())
                handler.postDelayed(this, 25_000L)
            }
        }
    }

    private val reconnect = Runnable {
        if (!closed && !connected) connectInternal()
    }

    fun connect() {
        if (closed) return
        handler.removeCallbacks(reconnect)
        connectInternal()
    }

    private fun nextRef(): String = (++ref).toString()

    private fun connectInternal() {
        if (closed || connected) return
        val url = "${Supabase.URL.replace("https://", "wss://")}/realtime/v1/websocket?apikey=${Supabase.KEY}&vsn=1.0.0"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed) {
                    webSocket.close(1000, "owner closed")
                    return
                }
                connected = true
                retrySeconds = 2L
                val changes = JSONArray()
                listOf("products", "categories", "banners", "coupons", "settings", "reviews").forEach { table ->
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", table))
                }
                val uid = api.userId
                if (!uid.isNullOrBlank()) {
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "users").put("filter", "id=eq.$uid"))
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "chats").put("filter", "id=eq.$uid"))
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "chat_messages").put("filter", "chat_id=eq.$uid"))
                }
                val payload = JSONObject()
                    .put("config", JSONObject()
                        .put("broadcast", JSONObject().put("ack", false).put("self", false))
                        .put("presence", JSONObject().put("key", ""))
                        .put("postgres_changes", changes))
                    .put("access_token", api.token ?: Supabase.KEY)
                webSocket.send(JSONObject()
                    .put("topic", "realtime:public:mm-bazaar-user")
                    .put("event", "phx_join")
                    .put("payload", payload)
                    .put("ref", nextRef())
                    .toString())
                handler.removeCallbacks(heartbeat)
                handler.postDelayed(heartbeat, 25_000L)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                val event = message.optString("event")
                if (event == "phx_reply") {
                    val status = message.optJSONObject("payload")?.optString("status")
                    if (status == "error") {
                        connected = false
                        webSocket.close(1011, "realtime join failed")
                    }
                    return
                }
                if (event == "postgres_changes" || text.contains("\"event\":\"postgres_changes\"")) {
                    handler.post { if (!closed) onChange() }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                handler.removeCallbacks(heartbeat)
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                handler.removeCallbacks(heartbeat)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                handler.removeCallbacks(heartbeat)
                handler.post { if (!closed) onChange() }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (closed) return
        handler.removeCallbacks(reconnect)
        val delay = retrySeconds.coerceAtMost(30L)
        retrySeconds = min(retrySeconds * 2L, 30L)
        handler.postDelayed(reconnect, delay * 1000L)
    }

    fun close() {
        closed = true
        connected = false
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(reconnect)
        socket?.close(1000, "activity paused")
        socket = null
    }
}
