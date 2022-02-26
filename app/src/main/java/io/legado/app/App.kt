package io.legado.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.os.Build
import androidx.multidex.MultiDexApplication
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.base.AppContextWrapper
import io.legado.app.constant.AppConst.channelIdDownload
import io.legado.app.constant.AppConst.channelIdReadAloud
import io.legado.app.constant.AppConst.channelIdWeb
import io.legado.app.help.AppConfig
import io.legado.app.help.CrashHandler
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.ThemeConfig.applyDayNight
import io.legado.app.help.http.cronet.CronetLoader
import io.legado.app.utils.defaultSharedPreferences
import splitties.systemservices.notificationManager

class App : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler(this)
        //预下载Cronet so
        CronetLoader.preDownload()
        createNotificationChannels()
        applyDayNight(this)
        LiveEventBus.config()
            .lifecycleObserverAlwaysActive(true)
            .autoClear(false)
        registerActivityLifecycleCallbacks(LifecycleHelp)
        copyAssetsFile("ssml.xml")
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(AppConfig)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(base))
    }

    private fun copyAssetsFile(sourceFilename: String) {
        val destFilename = filesDir.absolutePath + "/" + sourceFilename
        try {
            copyFromAssets(assets, sourceFilename, destFilename)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun copyFromAssets(assets: AssetManager, source: String, dest: String) {
        var `is`: InputStream? = null
        var fos: FileOutputStream? = null
        try {
            `is` = assets.open(source)
            fos = FileOutputStream(dest)
            val buffer = ByteArray(1024)
            var size = 0
            while (`is`.read(buffer, 0, 1024).also { size = it } >= 0) {
                fos.write(buffer, 0, size)
            }
        } finally {
            if (fos != null) {
                try {
                    fos.close()
                } finally {
                    `is`?.close()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES,
            Configuration.UI_MODE_NIGHT_NO -> applyDayNight(this)
        }
    }

    /**
     * 创建通知ID
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val downloadChannel = NotificationChannel(
            channelIdDownload,
            getString(R.string.action_download),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val readAloudChannel = NotificationChannel(
            channelIdReadAloud,
            getString(R.string.read_aloud),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val webChannel = NotificationChannel(
            channelIdWeb,
            getString(R.string.web_service),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        //向notification manager 提交channel
        notificationManager.createNotificationChannels(
            listOf(
                downloadChannel,
                readAloudChannel,
                webChannel
            )
        )
    }

}
