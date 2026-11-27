package com.mmbazaar.user.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Uses the same public Supabase project and publishable key as the existing MM BAZAAR web app. */
class Supabase(context: Context) {
    companion object {
        const val URL = "https://xayxwilczioscmldqxxj.supabase.co"
        const val KEY = "sb_publishable_piXc0n81QxyGhd-N2VPpvg_47MLQX41"
    }
    private val prefs = context.getSharedPreferences("mmb_user_session", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).build()
    private val media = "application/json; charset=utf-8".toMediaType()
    val userId: String? get() = prefs.getString("uid", null)
    val email: String? get() = prefs.getString("email", null)
    val token: String? get() = prefs.getString("access", null)

    private fun currentToken(): String {
        val access = token ?: return KEY
        val expires = prefs.getLong("expires_at", 0L)
        if (System.currentTimeMillis() / 1000L < expires - 90L) return access
        val refresh = prefs.getString("refresh", null) ?: return access
        return runCatching {
            val body = JSONObject().put("refresh_token", refresh).toString()
            http.newCall(Request.Builder().url("$URL/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", KEY).addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(media)).build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@runCatching access
                val obj = JSONObject(raw)
                val newAccess = obj.optString("access_token", access)
                prefs.edit().putString("access", newAccess).putString("refresh", obj.optString("refresh_token", refresh))
                    .putLong("expires_at", System.currentTimeMillis() / 1000L + obj.optLong("expires_in", 3600L)).apply()
                newAccess
            }
        }.getOrDefault(access)
    }
    private fun request(path: String, method: String = "GET", body: String? = null, prefer: String? = null): Request {
        val b = Request.Builder().url(path).addHeader("apikey", KEY).addHeader("Authorization", "Bearer ${token ?: KEY}")
        if (prefer != null) b.addHeader("Prefer", prefer)
        return when (method) {
            "POST" -> b.post((body ?: "{}").toRequestBody(media)).build()
            "PATCH" -> b.patch((body ?: "{}").toRequestBody(media)).build()
            "DELETE" -> b.delete().build()
            else -> b.get().build()
        }
    }
    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        val authorized = req.newBuilder().header("Authorization", "Bearer ${currentToken()}").build()
        http.newCall(authorized).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(raw).optString("message").ifBlank { JSONObject(raw).optString("error_description") } }.getOrDefault("")
                throw IllegalStateException(msg.ifBlank { "Supabase error ${res.code}: $raw" })
            }
            raw
        }
    }
    suspend fun select(table: String, query: String = "select=*"): JSONArray = JSONArray(execute(request("$URL/rest/v1/$table?$query")))
    suspend fun insert(table: String, payload: JSONObject): JSONArray = JSONArray(execute(request("$URL/rest/v1/$table", "POST", payload.toString(), "return=representation")))
    suspend fun upsert(table: String, payload: JSONObject): JSONArray = JSONArray(execute(request("$URL/rest/v1/$table", "POST", payload.toString(), "resolution=merge-duplicates,return=representation")))
    suspend fun update(table: String, query: String, payload: JSONObject): String = execute(request("$URL/rest/v1/$table?$query", "PATCH", payload.toString(), "return=representation"))
    suspend fun rpc(name: String, payload: JSONObject): String = execute(request("$URL/rest/v1/rpc/$name", "POST", payload.toString()))

    suspend fun signIn(email: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        http.newCall(Request.Builder().url("$URL/auth/v1/token?grant_type=password").addHeader("apikey", KEY).addHeader("Content-Type", "application/json").post(body.toRequestBody(media)).build()).execute().use { res ->
            val raw = res.body?.string().orEmpty(); val obj = JSONObject(raw)
            if (!res.isSuccessful) throw IllegalStateException(obj.optString("msg", obj.optString("message", "লগইন করা যায়নি")))
            saveSession(obj); obj
        }
    }
    suspend fun signUp(email: String, password: String, name: String, phone: String = ""): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email).put("password", password).put("data", JSONObject().put("name", name).put("phone", phone)).toString()
        http.newCall(Request.Builder().url("$URL/auth/v1/signup").addHeader("apikey", KEY).addHeader("Content-Type", "application/json").post(payload.toRequestBody(media)).build()).execute().use { res ->
            val raw = res.body?.string().orEmpty(); val obj = JSONObject(raw)
            if (!res.isSuccessful) throw IllegalStateException(obj.optString("msg", obj.optString("message", "রেজিস্ট্রেশন করা যায়নি")))
            if (obj.has("access_token")) saveSession(obj)
            obj
        }
    }
    suspend fun resetPasswordForEmail(email: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).toString()
        http.newCall(Request.Builder().url("$URL/auth/v1/recover").addHeader("apikey", KEY).addHeader("Content-Type", "application/json").post(body.toRequestBody(media)).build()).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException(runCatching { JSONObject(raw).optString("message") }.getOrDefault("পাসওয়ার্ড রিসেট লিংক পাঠানো যায়নি"))
        }
    }

    private fun saveSession(obj: JSONObject) {
        val u = obj.optJSONObject("user") ?: JSONObject()
        prefs.edit().putString("access", obj.optString("access_token")).putString("refresh", obj.optString("refresh_token"))
            .putLong("expires_at", System.currentTimeMillis() / 1000L + obj.optLong("expires_in", 3600L))
            .putString("uid", u.optString("id")).putString("email", u.optString("email")).apply()
    }
    fun signOut() { prefs.edit().clear().apply() }
    fun authHeaderPresent() = !token.isNullOrBlank()

}
