package io.legado.app.service

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.IntentHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.service.help.CacheBook
import io.legado.app.service.help.ReadAloud
import io.legado.app.service.help.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.*
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CacheAudioService2 : BaseService() {
    private val TAG = "CacheAudioService2"

    private val downloadMap = ConcurrentHashMap<String, CopyOnWriteArraySet<BookChapter>>()
    private var notificationContent = appCtx.getString(R.string.starting_download)
    private val executorService = Executors.newFixedThreadPool(AppConfig.threadCount)
    private lateinit var microAloudDownloader: MicroAloudDownloader
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable { upDownload() }
    private var totalParagraphCount = 0
    private var downloadedCount: AtomicInteger = AtomicInteger(0)
    private fun upDownload() {
        upNotification()
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, 1000)
    }

    private val notificationBuilder by lazy {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setContentTitle(getString(R.string.offline_cache))
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            IntentHelp.servicePendingIntent<CacheAudioService2>(this, IntentAction.stop)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        super.onCreate()
        upNotification()
        ttsFolder = externalCacheDir!!.absolutePath + File.separator + "httpTTS"
        microAloudDownloader =
            MicroAloudDownloader(this/*, MicroAloudDownloader.MicroProxy("127.0.0.1", 1080, "", "")*/)
        handler.postDelayed(runnable, 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.start -> prepareContentAndDownload(
                    intent.getStringExtra("bookUrl"),
                    intent.getIntExtra("start", 0),
                    intent.getIntExtra("end", 0)
                )
                IntentAction.stop -> stopDownload()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun stopDownload() {
        if (!executorService.isShutdown) {
            Thread {
                executorService.shutdown()
                val timeOut = !executorService.awaitTermination(1, TimeUnit.SECONDS)
                if (timeOut) {
                    executorService.shutdownNow()
                }
                downloadMap.clear()
            }.start()
        }
        handler.removeCallbacks(runnable)
        stopSelf()
    }

    private fun prepareContentAndDownload(bookUrl: String?, start: Int, end: Int) {
        bookUrl ?: return
        if (downloadMap.containsKey(bookUrl)) {
            notificationContent = getString(R.string.already_in_download)
            upNotification()
            toastOnUi(notificationContent)
            return
        }
        executorService.execute(object : Runnable {
            override fun run() {
                appDb.bookChapterDao.getChapterList(bookUrl, start, end).let {
                    if (it.isNotEmpty()) {
                        val book = getBook(bookUrl) ?: return
                        it.forEach { bookChapter ->
                            if (BookHelp.hasContent(book, bookChapter)) {
                                val chapterContent =
                                    BookHelp.getContent(book, bookChapter) ?: return@forEach
                                val contentProcessor = ContentProcessor(book.name, book.origin)
                                val splitContents =
                                    contentProcessor.getContent(
                                        book,
                                        bookChapter.title,
                                        chapterContent
                                    )
                                splitContents.forEach { _ ->
                                    totalParagraphCount++
                                }
                            }
                        }
                    }
                }
                if (executorService.isShutdown) {
                    LogUtils.d(TAG, "interrupted before getChapterList")
                    return
                }
                appDb.bookChapterDao.getChapterList(bookUrl, start, end).let {
                    if (it.isNotEmpty()) {
                        val chapters = CopyOnWriteArraySet<BookChapter>()
                        chapters.addAll(it)
                        downloadMap[bookUrl] = chapters
                        val book = getBook(bookUrl) ?: return
                        it.forEach { bookChapter ->
                            if (BookHelp.hasContent(book, bookChapter)) {
                                if (executorService.isShutdown) {
                                    LogUtils.d(TAG, "interrupted before in getChapterList loop")
                                    return
                                }
                                val chapterContent =
                                    BookHelp.getContent(book, bookChapter) ?: return@forEach
                                val contentProcessor = ContentProcessor(book.name, book.origin)
                                val splitContents =
                                    contentProcessor.getContent(
                                        book,
                                        bookChapter.title,
                                        chapterContent
                                    )
                                splitContents.forEach { paragraph ->
                                    LogUtils.d(TAG, "startDownload.$paragraph")
                                    val textChapter = ChapterProvider.getTextChapter(
                                        book, bookChapter, splitContents, ReadBook.chapterSize
                                    )
                                    val fileName =
                                        md5SpeakFileName(
                                            textChapter,
                                            //todo,路径
                                            ReadAloud.httpTTS?.url ?: "",
                                            AppConfig.ttsSpeechRate.toString(),
                                            paragraph
                                        )
                                    val task = downloadTask(fileName, paragraph)
                                    try {
                                        executorService.execute(task)
                                    } catch (e: Exception) {
                                        LogUtils.d(TAG, "add task to executorService fail")
                                    }
                                }
                            }
                        }
                    } else {
                        CacheBook.addLog("${getBook(bookUrl)?.name} is empty")
                    }
                }
            }
        })
    }

    private fun md5SpeakFileName(
        textChapter: TextChapter,
        url: String,
        ttsConfig: String,
        content: String
    ): String {
        return MD5Utils.md5Encode16(textChapter.title) + "_" + MD5Utils.md5Encode16("$url-|-$ttsConfig-|-$content")
    }

    /**
     * 更新通知
     */
    private fun upNotification() {
        notificationBuilder.setContentText(notificationContent)
        val notification = notificationBuilder.build()
        startForeground(AppConst.notificationIdDownload, notification)
    }

    private fun upNotification(
        downloadedCount: Int?,
        totalCount: Int?
    ) {
        if (downloadedCount == totalCount) {
            stopDownload()
        } else {
            notificationContent =
                "进度:$downloadedCount/$totalCount"
        }
    }

    private fun getBook(bookUrl: String): Book? {
        return appDb.bookDao.getBook(bookUrl)
    }

    private lateinit var ttsFolder: String

    private fun speakFilePath() = ttsFolder + File.separator

    private fun hasSpeakFile(name: String) =
        FileUtils.exist("${speakFilePath()}$name.mp3")

    private fun downloadTask(fileName: String, paragraph: String): Runnable {
        return Runnable {
            if (!hasSpeakFile(fileName)) { //已经下载好的语音缓存
                if (appCtx.getPrefLong(PreferKey.speakEngine) == -30L) {
                    try {
                        microAloudDownloader.download(
                            paragraph,
                            AppConfig.ttsSpeechRate
                        )
                        LogUtils.d(TAG, "downloadSuccess.$paragraph")
                    } catch (e: InterruptedException) {
                        LogUtils.d(TAG, "microAloudDownloader interrupt")
                    }
                } else {
                    try {
                        runBlocking {
                            ReadAloud.httpTTS?.let {
                                AnalyzeUrl(
                                    it.url,
                                    speakText = paragraph,
                                    speakSpeed = AppConfig.ttsSpeechRate
                                ).getByteArray()
                            }
                        }?.let {
                            val file = getSpeakFileAsMd5IfNotExist(fileName)
                            file.writeBytes(it)
                            LogUtils.d(TAG, "downloadSuccess.$paragraph")
                        } ?: run {
                            LogUtils.d(TAG, "downloadFail.$paragraph")
                        }
                    } catch (e: InterruptedException) {
                        LogUtils.d(TAG, "runBlocking interrupt")
                    }
                }
            } else {
                LogUtils.d(TAG, "already exits.$paragraph")
            }
            upNotification(downloadedCount.addAndGet(1), totalParagraphCount)
        }
    }

    private fun getSpeakFileAsMd5IfNotExist(name: String): File =
        FileUtils.createFileIfNotExist("${speakFilePath()}$name.mp3")

}