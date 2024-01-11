// To parse the JSON, install Klaxon and do:
//
//   val apifoxModel = ApifoxModel.fromJson(jsonString)

package io.legado.app.service

import com.beust.klaxon.*

private val klaxon = Klaxon()

fun String.toApifoxModel():ApifoxModel? {
    return ApifoxModel.fromJson(this)
}

data class ApifoxModel (
    val choices: List<Choice>,
    val created: Long,
    val id: String,

    @Json(name = "object")
    val apifoxModelObject: String,

    val usage: Usage
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<ApifoxModel>(json)
    }
}

data class Choice (
    @Json(name = "finish_reason")
    val finishReason: String? = null,

    val index: Long? = null,
    val message: Message? = null
)

data class Message (
    val content: String,
    val role: String
)

data class Usage (
    @Json(name = "completion_tokens")
    val completionTokens: Long,

    @Json(name = "prompt_tokens")
    val promptTokens: Long,

    @Json(name = "total_tokens")
    val totalTokens: Long
)
