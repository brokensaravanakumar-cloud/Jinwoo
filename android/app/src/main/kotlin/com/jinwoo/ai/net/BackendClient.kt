package com.jinwoo.ai.net

import com.jinwoo.ai.data.SecureStore
import com.jinwoo.ai.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendException(val code: String, message: String, val http: Int = 0) : Exception(message)

data class ChatMessage(val role: String, val content: String)

/** Talks to the JIN WOO FastAPI backend. Provider API keys never exist in the app: only an optional backend access token. */
class BackendClient(private val settings: SettingsRepository, private val secure: SecureStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String): String {
        val base = settings.current.backendUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw BackendException("not_configured", "Backend URL is not set.")
        if (base.toHttpUrlOrNull() == null) throw BackendException("not_configured", "Backend URL is invalid.")
        return base + path
    }

    private fun builder(path: String): Request.Builder {
        val b = Request.Builder().url(url(path))
        val token = secure.get(SecureStore.TOKEN)
        if (token.isNotEmpty()) b.header("Authorization", "Bearer $token")
        return b
    }

    private fun parseError(code: Int, body: String?): BackendException = try {
        val e = JSONObject(body ?: "").getJSONObject("error")
        BackendException(e.optString("code", "error"), e.optString("message", "Request failed"), code)
    } catch (ignored: Exception) {
        BackendException("http_$code", "Server returned HTTP $code", code)
    }

    private suspend fun <T> execute(request: Request, parse: (okhttp3.Response) -> T): T = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw parseError(r.code, r.body?.string())
                parse(r)
            }
        } catch (e: BackendException) {
            throw e
        } catch (e: IOException) {
            throw BackendException("network_error", "Cannot reach the backend (${e.message}).")
        }
    }

    suspend fun status(): JSONObject = execute(builder("/v1/status").build()) { JSONObject(it.body!!.string()) }

    suspend fun models(provider: String): List<String> =
        execute(builder("/v1/models?provider=$provider").build()) { r ->
            val arr = JSONObject(r.body!!.string()).getJSONArray("models")
            List(arr.length()) { arr.getString(it) }
        }

    /** Sends a tiny prompt through the full backend -> provider path. Returns the model's short reply. */
    suspend fun test(provider: String, model: String): String {
        val body = JSONObject().put("provider", provider).apply { if (model.isNotBlank()) put("model", model) }
        return execute(builder("/v1/test").post(body.toString().toRequestBody(json)).build()) {
            JSONObject(it.body!!.string()).optString("reply", "(empty reply)")
        }
    }

    /** Returns audio bytes (mp3) from the optional backend TTS endpoint. */
    suspend fun tts(text: String): ByteArray {
        val body = JSONObject().put("text", text)
        return execute(builder("/v1/tts").post(body.toString().toRequestBody(json)).build()) { it.body!!.bytes() }
    }

    /** Streams reply text deltas from POST /v1/chat/stream (SSE: `data: {"delta": "..."}` ... `data: {"done": true}`). */
    fun streamChat(provider: String, model: String, messages: List<ChatMessage>, system: String): Flow<String> = callbackFlow {
        val body = JSONObject()
            .put("provider", provider)
            .put("system", system)
            .put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
        if (model.isNotBlank()) body.put("model", model)

        val call = try {
            http.newCall(builder("/v1/chat/stream").post(body.toString().toRequestBody(json)).build())
        } catch (e: BackendException) { close(e); return@callbackFlow }

        launch(Dispatchers.IO) {
            try {
                call.execute().use { r ->
                    if (!r.isSuccessful) throw parseError(r.code, r.body?.string())
                    val src = r.body!!.source()
                    while (true) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = JSONObject(line.removePrefix("data:").trim())
                        if (payload.has("error")) {
                            val e = payload.getJSONObject("error")
                            throw BackendException(e.optString("code", "error"), e.optString("message", "Stream failed"))
                        }
                        if (payload.optBoolean("done")) break
                        trySend(payload.optString("delta"))
                    }
                }
                close()
            } catch (e: BackendException) {
                close(e)
            } catch (e: IOException) {
                if (isActive) close(BackendException("network_error", "Connection lost (${e.message}).")) else close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose { call.cancel() }
    }
}
