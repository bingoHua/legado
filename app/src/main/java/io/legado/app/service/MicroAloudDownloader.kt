package io.legado.app.service

import android.content.Context
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import freemarker.template.Configuration
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.awaitAll
import java.io.StringWriter
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class MicroAloudDownloader constructor(context: Context, private val proxy: MicroProxy? = null) {
    private var cfg: Configuration = Configuration(Configuration.VERSION_2_3_24)
    private var synthesizer: SpeechSynthesizer
    private var speechConfig: SpeechConfig

    init {
        cfg.setDirectoryForTemplateLoading(context.filesDir)
        cfg.defaultEncoding = "UTF-8"
        speechConfig =
            SpeechConfig.fromSubscription("9644ad9e4a40402a83462228bfeca076", "eastus").apply {
                proxy?.let {
                    this.setProxy(it.proxyHostName, it.port, it.userName, it.password)
                }
            }
        synthesizer = SpeechSynthesizer(speechConfig, null)
    }

    class MicroProxy constructor(
        val proxyHostName: String,
        val port: Int,
        val userName: String,
        val password: String
    )

    fun download(text: String, rate: Int): ByteArray? {
        val result = synthesizer.SpeakSsml(text.toSsml(cfg, rate))
        return when (result.reason) {
            ResultReason.Canceled -> {
                val cancellationDetails =
                    SpeechSynthesisCancellationDetails.fromResult(result).toString()
                LogUtils.d(
                    "MicroAloudDownloader", "Error synthesizing. Error detail: " +
                            System.lineSeparator() + cancellationDetails +
                            System.lineSeparator() + "Did you update the subscription info?"
                )
                return null
            }
            ResultReason.SynthesizingAudioCompleted -> {
                result.audioData
            }
            else -> null
        }
    }
}

fun String.toSsml(cfg: Configuration, rate: Int): String {
    val root = HashMap<String, Any>()
    root["text"] = this
    root["rate"] = rate
    val temp = cfg.getTemplate("ssml.xml")
    val out = StringWriter()
    temp.process(root, out)
    return out.toString()
}

