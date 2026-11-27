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

/** Supabase Realtime Phoenix subscription for store updates and the signed-in customer's chat. */
class StoreRealtime(private val api: Supabase, private val onChange: () -> Unit) {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    @Volatile private var closed = false
    private var ref = 0
    private val heartbeat = object : Runnable {
        override fun run() {
            val ws = socket
            if (!closed && ws != null) {
                ws.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                    .put("payload", JSONObject()).put("ref", (++ref).toString()).toString())
                handler.postDelayed(this, 25_000L)
            }
        }
    }

    fun connect() {
        closed = false
        val url = "${Supabase.URL.replace("https://", "wss://")}/realtime/v1/websocket?apikey=${Supabase.KEY}&vsn=1.0.0"
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changes = JSONArray()
                listOf("products", "categories", "banners", "coupons", "settings").forEach { table ->
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", table))
                }
                val uid = api.userId
                if (!uid.isNullOrBlank()) {
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "users").put("filter", "id=eq.$uid"))
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "chat_messages").put("filter", "chat_id=eq.$uid"))
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "chats").put("filter", "id=eq.$uid"))
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "reviews"))
                }
                val payload = JSONObject().put("config", JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", changes))
                    .put("access_token", api.token ?: Supabase.KEY)
                webSocket.send(JSONObject().put("topic", "realtime:public:mm-bazaar-user")
                    .put("event", "phx_join").put("payload", payload).put("ref", (++ref).toString()).toString())
                handler.removeCallbacks(heartbeat)
                handler.postDelayed(heartbeat, 25_000L)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val changed = text.contains("postgres_changes") || text.contains("INSERT") ||
                    text.contains("UPDATE") || text.contains("DELETE")
                if (changed && !closed) onChange()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed) onChange()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handler.removeCallbacks(heartbeat)
            }
        })
    }

    fun close() {
        closed = true
        handler.removeCallbacks(heartbeat)
        socket?.close(1000, "activity paused")
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}
