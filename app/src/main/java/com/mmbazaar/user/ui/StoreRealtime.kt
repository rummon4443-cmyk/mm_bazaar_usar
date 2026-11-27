package com.mmbazaar.user.ui

import com.mmbazaar.user.data.Supabase
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Supabase Realtime Phoenix websocket subscription for store/customer data. */
class StoreRealtime(private val api: Supabase, private val onChange: () -> Unit) {
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var closed = false
    private var ref = 0

    fun connect() {
        val url = "${Supabase.URL.replace("https://", "wss://")}/realtime/v1/websocket?apikey=${Supabase.KEY}&vsn=1.0.0"
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changes = JSONArray()
                listOf("products", "categories", "banners", "coupons", "settings").forEach { table ->
                    changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", table))
                }
                val uid = api.userId
                if (!uid.isNullOrBlank()) changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "users").put("filter", "id=eq.$uid"))
                if (!uid.isNullOrBlank()) changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", "chat_messages").put("filter", "chat_id=eq.$uid"))
                val payload = JSONObject().put("config", JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", changes))
                    .put("access_token", api.token ?: Supabase.KEY)
                webSocket.send(JSONObject().put("topic", "realtime:public:mm-bazaar-user").put("event", "phx_join").put("payload", payload).put("ref", (++ref).toString()).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("postgres_changes") || text.contains("INSERT") || text.contains("UPDATE") || text.contains("DELETE")) onChange()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (!closed) onChange() }
        })
    }
    fun close() { closed = true; socket?.close(1000, "activity paused"); socket = null; client.dispatcher.executorService.shutdown() }
}
