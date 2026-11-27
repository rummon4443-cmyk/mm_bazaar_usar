package com.mmbazaar.user.ui

import com.mmbazaar.user.data.Supabase
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Supabase Realtime Phoenix websocket subscription with heartbeat and reconnect backoff. */
class StoreRealtime(
    private val api: Supabase,
    private val onChange: (String) -> Unit
) {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var socket: WebSocket? = null
    @Volatile private var closed = false
    private var ref = 0
    private var reconnectAttempt = 0
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var reconnectTask: ScheduledFuture<*>? = null

    @Synchronized
    fun connect() {
        closed = false
        reconnectAttempt = 0
        openSocket()
    }

    @Synchronized
    private fun openSocket() {
        if (closed) return
        val url = "${Supabase.URL.replace("https://", "wss://")}/realtime/v1/websocket?apikey=${Supabase.KEY}&vsn=1.0.0"
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
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
                val payload = JSONObject().put("config", JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", changes))
                    .put("access_token", api.token ?: Supabase.KEY)
                webSocket.send(JSONObject().put("topic", "realtime:public:mm-bazaar-user")
                    .put("event", "phx_join").put("payload", payload).put("ref", nextRef()).toString())
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val root = JSONObject(text)
                    val event = root.optString("event")
                    if (event == "phx_error" || event == "phx_close") {
                        scheduleReconnect()
                        return
                    }
                    val payload = root.optJSONObject("payload") ?: JSONObject()
                    val data = payload.optJSONObject("data") ?: payload
                    val table = data.optString("table").ifBlank {
                        data.optJSONObject("record")?.optString("table").orEmpty()
                    }
                    if (table.isNotBlank()) onChange(table)
                }.onFailure {
                    if (text.contains("postgres_changes") || text.contains("INSERT") || text.contains("UPDATE") || text.contains("DELETE")) {
                        onChange("")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                stopHeartbeat()
                if (!closed) {
                    onChange("")
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopHeartbeat()
                if (!closed) scheduleReconnect()
            }
        })
    }

    @Synchronized
    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatTask?.cancel(false)
        heartbeatTask = scheduler.scheduleAtFixedRate({
            if (!closed) {
                webSocket.send(JSONObject().put("topic", "phoenix")
                    .put("event", "heartbeat").put("payload", JSONObject())
                    .put("ref", nextRef()).toString())
            }
        }, 25, 25, TimeUnit.SECONDS)
    }

    @Synchronized
    private fun stopHeartbeat() {
        heartbeatTask?.cancel(false)
        heartbeatTask = null
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (closed || scheduler.isShutdown || reconnectTask?.isDone == false) return
        stopHeartbeat()
        val delay = (1L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(30L)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(5)
        reconnectTask = scheduler.schedule({ openSocket() }, delay, TimeUnit.SECONDS)
    }

    @Synchronized
    private fun nextRef(): String = (++ref).toString()

    @Synchronized
    fun close() {
        closed = true
        stopHeartbeat()
        reconnectTask?.cancel(true)
        reconnectTask = null
        socket?.close(1000, "activity paused")
        socket = null
        scheduler.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }
}
