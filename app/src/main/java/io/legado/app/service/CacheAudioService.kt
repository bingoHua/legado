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
import io.legado.app.help.AppConfig.threadCount
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.IntentHelp
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.service.help.CacheBook
import io.legado.app.service.help.ReadAloud
import io.legado.app.service.help.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class CacheAudioService : BaseService() {

    private val TAG = "CacheAudioService"

    private var searchPool =
        Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
    private var notificationContent = appCtx.getString(R.string.starting_download)
    private val downloadMap = ConcurrentHashMap<String, CopyOnWriteArraySet<BookChapter>>()
    private val downloadCount = ConcurrentHashMap<String, AudioDownloadCount>()
    private val downloadingList = CopyOnWriteArraySet<String>()
    private var runnable: Runnable = Runnable { upDownload() }
    private val handler = Handler(Looper.getMainLooper())
    private var tasks = CompositeCoroutine()
    private lateinit var microAloudDownloader: MicroAloudDownloader
    private lateinit var ttsFolder: String

    @Volatile
    private var downloadingCount = 0

    private val notificationBuilder by lazy {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setContentTitle(getString(R.string.offline_cache))
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            IntentHelp.servicePendingIntent<CacheAudioService>(this, IntentAction.stop)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        super.onCreate()
        upNotification()
        microAloudDownloader =
            MicroAloudDownloader(this, MicroAloudDownloader.MicroProxy("127.0.0.1", 1080, "", ""))
        ttsFolder = externalCacheDir!!.absolutePath + File.separator + "httpTTS"
        handler.postDelayed(runnable, 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.start -> addDownloadData(
                    intent.getStringExtra("bookUrl"),
                    intent.getIntExtra("start", 0),
                    intent.getIntExtra("end", 0)
                )
                IntentAction.stop -> stopDownload()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        tasks.clear()
        searchPool.close()
        downloadMap.clear()
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    private fun getBook(bookUrl: String): Book? {
        return appDb.bookDao.getBook(bookUrl)
    }

    private fun addDownloadData(bookUrl: String?, start: Int, end: Int) {
        bookUrl ?: return
        if (downloadMap.containsKey(bookUrl)) {
            notificationContent = getString(R.string.already_in_download)
            upNotification()
            toastOnUi(notificationContent)
            return
        }
        downloadCount[bookUrl] = AudioDownloadCount()
        execute {
            appDb.bookChapterDao.getChapterList(bookUrl, start, end).let {
                if (it.isNotEmpty()) {
                    val chapters = CopyOnWriteArraySet<BookChapter>()
                    chapters.addAll(it)
                    downloadMap[bookUrl] = chapters
                } else {
                    CacheBook.addLog("${getBook(bookUrl)?.name} is empty")
                }
            }
            for (i in 0 until threadCount) {
                if (downloadingCount < threadCount) {
                    download()
                }
            }
        }
    }

    private fun download() {
        downloadingCount += 1
        val task = Coroutine.async(this, context = searchPool) {
            if (!isActive) return@async
            val bookChapter: BookChapter? = synchronized(this@CacheAudioService) {
                downloadMap.forEach {
                    it.value.forEach { chapter ->
                        if (!downloadingList.contains(chapter.url)) {
                            downloadingList.add(chapter.url)
                            return@synchronized chapter
                        }
                    }
                }
                return@synchronized null
            }
            if (bookChapter == null) {
                postDownloading(false)
            } else {
                val book = getBook(bookChapter.bookUrl)
                if (book == null) {
                    postDownloading(false)
                    return@async
                }
                if (BookHelp.hasContent(book, bookChapter)) {
                    val chapterContent = BookHelp.getContent(book, bookChapter)
                    if (chapterContent == null) {
                        postDownloading(false)
                        return@async
                    }
                    val contentProcessor = ContentProcessor(book.name, book.origin)
                    val splitContents =
                        contentProcessor.getContent(book, bookChapter.title, chapterContent)
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
                        if (!hasSpeakFile(fileName)) { //已经下载好的语音缓存
                            if (appCtx.getPrefLong(PreferKey.speakEngine) == -30L) {
                                microAloudDownloader.download(
                                    paragraph,
                                    AppConfig.ttsSpeechRate
                                )
                            } else {
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
                        } else {
                            LogUtils.d(TAG, "already exits.$paragraph")
                        }
                    }
                }
                downloadCount[book.bookUrl]?.increaseSuccess()
                downloadCount[book.bookUrl]?.increaseFinished()
                downloadCount[book.bookUrl]?.let {
                    upNotification(it, downloadMap[book.bookUrl]?.size, bookChapter.title)
                }
                postDownloading(false)
            }
        }
        tasks.add(task)

    }

    private fun hasSpeakFile(name: String) =
        FileUtils.exist("${speakFilePath()}$name.mp3")

    private fun postDownloading(hasChapter: Boolean) {
        downloadingCount -= 1
        if (hasChapter) {
            download()
        } else {
            if (downloadingCount < 1) {
                stopDownload()
            }
        }
    }

    private fun stopDownload() {
        tasks.clear()
        stopSelf()
    }


    private fun getSpeakFileAsMd5IfNotExist(name: String): File =
        FileUtils.createFileIfNotExist("${speakFilePath()}$name.mp3")

    private fun speakFilePath() = ttsFolder + File.separator

    private fun md5SpeakFileName(
        textChapter: TextChapter,
        url: String,
        ttsConfig: String,
        content: String
    ): String {
        return MD5Utils.md5Encode16(textChapter.title) + "_" + MD5Utils.md5Encode16("$url-|-$ttsConfig-|-$content")
    }

    private fun upNotification(
        downloadCount: AudioDownloadCount,
        totalCount: Int?,
        content: String
    ) {
        notificationContent =
            "进度:" + downloadCount.downloadFinishedCount + "/" + totalCount + ",成功:" + downloadCount.successCount + "," + content
    }

    private fun upDownload() {
        upNotification()
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, 1000)
    }

    /**
     * 更新通知
     */
    private fun upNotification() {
        notificationBuilder.setContentText(notificationContent)
        val notification = notificationBuilder.build()
        startForeground(AppConst.notificationIdDownload, notification)
    }

    class AudioDownloadCount {
        @Volatile
        var downloadFinishedCount = 0 // 下载完成的条目数量

        @Volatile
        var successCount = 0 //下载成功的条目数量

        fun increaseSuccess() {
            ++successCount
        }

        fun increaseFinished() {
            ++downloadFinishedCount
        }
    }

}