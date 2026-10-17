package com.wuliao.huanye.data

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

/**
 * AI 对话（OpenAI 兼容流式 SSE）
 * 兼容：DeepSeek / Moonshot / 智谱 / 通义 / Agnes / 任意 OpenAI 格式
 * @author 无聊
 */
object AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Cfg(val baseUrl: String, val apiKey: String, val model: String)

    private var cfg: Cfg? = null

    fun setConfig(c: Cfg) { cfg = c }
    fun config(): Cfg? = cfg

    /**
     * 流式对话。每收到一个 token 就回调一次。
     * @param system 角色设定（卡片的 setting/generic）
     * @param history 历史消息 [(role, content)]
     * @param user 用户输入
     */
    suspend fun chatStream(
        system: String,
        history: List<Pair<String, String>>,
        user: String,
        onDelta: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val c = cfg ?: throw IllegalStateException("请先配置 AI 接口")

        val msgs = JSONArray()
        if (system.isNotBlank())
            msgs.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (r, ct) ->
            msgs.put(JSONObject().put("role", r).put("content", ct))
        }
        msgs.put(JSONObject().put("role", "user").put("content", user))

        val body = JSONObject()
            .put("model", c.model)
            .put("messages", msgs)
            .put("stream", true)
            .put("temperature", 0.85)
            .toString()

        val url = c.baseUrl.trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${c.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(300) ?: ""
                throw IllegalStateException("HTTP ${resp.code} $err")
            }
            val src = resp.body?.source() ?: throw IllegalStateException("空响应")
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                try {
                    val j = JSONObject(payload)
                    val choices = j.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    val piece = delta.optString("content", "")
                    if (piece.isNotEmpty()) onDelta(piece)
                } catch (e: Exception) { /* 忽略心跳/坏包 */ }
            }
        }
    }
}
