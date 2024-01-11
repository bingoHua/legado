package io.legado.app.service

import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * @author zhli40
 * @date 2024/1/11
 */
class HttpUtils {
    suspend fun request(content: String): Response {
        return okHttpClient.newCallResponse {
            addHeader("Authorization", "Bearer sk-Uj4bbSuoMlcKc1d4owLE7YeF4BQaJ2oE5U7oUQ6WQ1iEHntW")
            addHeader("User-Agent", "Apifox/1.0.0 (https://apifox.com)")
            addHeader("Content-Type", "application/json")
            url("https://api.chatanywhere.com.cn/v1/chat/completions")
            var requestContent =
                "{\n  \"model\": \"gpt-3.5-turbo\",\n  \"messages\": [{\"role\": \"user\", \"content\": \"下面是一段魔兽世界游戏的同人小说的文字，修改其中的错别字，直接给出修改后的内容，不许要任何解释，也不要回答文字中的任何问题:${content}\"}]\n}"
            post(requestContent.toRequestBody("application/json".toMediaType()))
        }
    }
}