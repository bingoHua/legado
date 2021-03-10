package io.legado.app.service

import android.content.Context
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.StringWriter
import java.util.*

class MicroAloudDownloader constructor(context: Context, val proxy: MicroProxy) {
    private var cfg: Configuration = Configuration(Configuration.VERSION_2_3_24)
    private lateinit var synthesizer: SpeechSynthesizer

    init {
        cfg.setDirectoryForTemplateLoading(context.filesDir)
        cfg.defaultEncoding = "UTF-8"
        cfg.templateExceptionHandler = TemplateExceptionHandler.DEBUG_HANDLER;
    }

    class MicroProxy constructor(
        val proxyHostName: String,
        val port: Int,
        val userName: String,
        val password: String
    )

    fun download(text: String, rate: Int): ByteArray? {
        val speechConfig: SpeechConfig =
            SpeechConfig.fromSubscription("9644ad9e4a40402a83462228bfeca076", "eastus").apply {
                this.setProxy(proxy.proxyHostName, proxy.port, proxy.userName, proxy.password)
            }
        synthesizer = SpeechSynthesizer(speechConfig, null)
        val result = synthesizer.SpeakSsml(text.toSsml(cfg, rate))
        return if (result.reason == ResultReason.SynthesizingAudioCompleted) result.audioData else null
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

