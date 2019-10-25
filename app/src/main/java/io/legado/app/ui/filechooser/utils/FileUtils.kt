package io.legado.app.ui.filechooser.utils

import android.webkit.MimeTypeMap
import androidx.annotation.IntDef
import java.io.*
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * 文件处理
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"></uses-permission>
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"></uses-permission>
 *
 * @author 李玉江[QQ:1023694760]
 * @since 2014-4-18
 */
object FileUtils {
    const val BY_NAME_ASC = 0
    const val BY_NAME_DESC = 1
    const val BY_TIME_ASC = 2
    const val BY_TIME_DESC = 3
    const val BY_SIZE_ASC = 4
    const val BY_SIZE_DESC = 5
    const val BY_EXTENSION_ASC = 6
    const val BY_EXTENSION_DESC = 7

    @IntDef(value = [BY_NAME_ASC, BY_NAME_DESC, BY_TIME_ASC, BY_TIME_DESC, BY_SIZE_ASC, BY_SIZE_DESC, BY_EXTENSION_ASC, BY_EXTENSION_DESC])
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class SortType

    /**
     * 将目录分隔符统一为平台默认的分隔符，并为目录结尾添加分隔符
     */
    fun separator(path: String): String {
        var path = path
        val separator = File.separator
        path = path.replace("\\", separator)
        if (!path.endsWith(separator)) {
            path += separator
        }
        return path
    }

    fun closeSilently(c: Closeable?) {
        if (c == null) {
            return
        }
        try {
            c.close()
        } catch (ignored: IOException) {
        }

    }

    /**
     * 列出指定目录下的所有子目录
     */
    @JvmOverloads
    fun listDirs(
        startDirPath: String,
        excludeDirs: Array<String?>? = null, @SortType sortType: Int = BY_NAME_ASC
    ): Array<File?> {
        var excludeDirs = excludeDirs
        val dirList = ArrayList<File>()
        val startDir = File(startDirPath)
        if (!startDir.isDirectory) {
            return arrayOfNulls(0)
        }
        val dirs = startDir.listFiles(FileFilter { f ->
            if (f == null) {
                return@FileFilter false
            }
            f.isDirectory
        })
            ?: return arrayOfNulls(0)
        if (excludeDirs == null) {
            excludeDirs = arrayOfNulls(0)
        }
        for (dir in dirs) {
            val file = dir.absoluteFile
            if (!excludeDirs.contentDeepToString().contains(file.name)) {
                dirList.add(file)
            }
        }
        when (sortType) {
            BY_NAME_ASC -> Collections.sort(dirList, SortByName())
            BY_NAME_DESC -> {
                Collections.sort(dirList, SortByName())
                dirList.reverse()
            }
            BY_TIME_ASC -> Collections.sort(dirList, SortByTime())
            BY_TIME_DESC -> {
                Collections.sort(dirList, SortByTime())
                dirList.reverse()
            }
            BY_SIZE_ASC -> Collections.sort(dirList, SortBySize())
            BY_SIZE_DESC -> {
                Collections.sort(dirList, SortBySize())
                dirList.reverse()
            }
            BY_EXTENSION_ASC -> Collections.sort(dirList, SortByExtension())
            BY_EXTENSION_DESC -> {
                Collections.sort(dirList, SortByExtension())
                dirList.reverse()
            }
        }
        return dirList.toTypedArray()
    }

    /**
     * 列出指定目录下的所有子目录及所有文件
     */
    @JvmOverloads
    fun listDirsAndFiles(
        startDirPath: String,
        allowExtensions: Array<String?>? = null
    ): Array<File?>? {
        val dirs: Array<File?>?
        val files: Array<File?>? = if (allowExtensions == null) {
            listFiles(startDirPath)
        } else {
            listFiles(startDirPath, allowExtensions)
        }
        val dirsAndFiles: Array<File?>
        dirs = listDirs(startDirPath)
        if (files == null) {
            return null
        }
        dirsAndFiles = arrayOfNulls(dirs.size + files.size)
        System.arraycopy(dirs, 0, dirsAndFiles, 0, dirs.size)
        System.arraycopy(files, 0, dirsAndFiles, dirs.size, files.size)
        return dirsAndFiles
    }

    /**
     * 列出指定目录下的所有文件
     */
    @JvmOverloads
    fun listFiles(
        startDirPath: String,
        filterPattern: Pattern? = null, @SortType sortType: Int = BY_NAME_ASC
    ): Array<File?> {
        val fileList = ArrayList<File>()
        val f = File(startDirPath)
        if (!f.isDirectory) {
            return arrayOfNulls(0)
        }
        val files = f.listFiles(FileFilter { f ->
            if (f == null) {
                return@FileFilter false
            }
            if (f.isDirectory) {
                return@FileFilter false
            }

            filterPattern?.matcher(f.name)?.find() ?: true
        })
            ?: return arrayOfNulls(0)
        for (file in files) {
            fileList.add(file.absoluteFile)
        }
        when (sortType) {
            BY_NAME_ASC -> Collections.sort(fileList, SortByName())
            BY_NAME_DESC -> {
                Collections.sort(fileList, SortByName())
                fileList.reverse()
            }
            BY_TIME_ASC -> Collections.sort(fileList, SortByTime())
            BY_TIME_DESC -> {
                Collections.sort(fileList, SortByTime())
                fileList.reverse()
            }
            BY_SIZE_ASC -> Collections.sort(fileList, SortBySize())
            BY_SIZE_DESC -> {
                Collections.sort(fileList, SortBySize())
                fileList.reverse()
            }
            BY_EXTENSION_ASC -> Collections.sort(fileList, SortByExtension())
            BY_EXTENSION_DESC -> {
                Collections.sort(fileList, SortByExtension())
                fileList.reverse()
            }
        }
        return fileList.toTypedArray()
    }

    /**
     * 列出指定目录下的所有文件
     */
    fun listFiles(startDirPath: String, allowExtensions: Array<String?>): Array<File?>? {
        val file = File(startDirPath)
        return file.listFiles { dir, name ->
            //返回当前目录所有以某些扩展名结尾的文件
            val extension = getExtension(name)
            allowExtensions.contentDeepToString().contains(extension)
        }
    }

    /**
     * 列出指定目录下的所有文件
     */
    fun listFiles(startDirPath: String, allowExtension: String?): Array<File?>? {
        return listFiles(startDirPath, arrayOf(allowExtension))
    }

    /**
     * 判断文件或目录是否存在
     */
    fun exist(path: String): Boolean {
        val file = File(path)
        return file.exists()
    }

    /**
     * 删除文件或目录
     */
    @JvmOverloads
    fun delete(file: File, deleteRootDir: Boolean = false): Boolean {
        var result = false
        if (file.isFile) {
            //是文件
            result = deleteResolveEBUSY(file)
        } else {
            //是目录
            val files = file.listFiles() ?: return false
            if (files.isEmpty()) {
                result = deleteRootDir && deleteResolveEBUSY(file)
            } else {
                for (f in files) {
                    delete(f, deleteRootDir)
                    result = deleteResolveEBUSY(f)
                }
            }
            if (deleteRootDir) {
                result = deleteResolveEBUSY(file)
            }
        }
        return result
    }

    /**
     * bug: open failed: EBUSY (Device or resource busy)
     * fix: http://stackoverflow.com/questions/11539657/open-failed-ebusy-device-or-resource-busy
     */
    private fun deleteResolveEBUSY(file: File): Boolean {
        // Before you delete a Directory or File: rename it!
        val to = File(file.absolutePath + System.currentTimeMillis())

        file.renameTo(to)
        return to.delete()
    }

    /**
     * 删除文件或目录
     */
    @JvmOverloads
    fun delete(path: String, deleteRootDir: Boolean = false): Boolean {
        val file = File(path)

        return if (file.exists()) {
            delete(file, deleteRootDir)
        } else false
    }

    /**
     * 复制文件为另一个文件，或复制某目录下的所有文件及目录到另一个目录下
     */
    fun copy(src: String, tar: String): Boolean {
        val srcFile = File(src)
        return srcFile.exists() && copy(srcFile, File(tar))
    }

    /**
     * 复制文件或目录
     */
    fun copy(src: File, tar: File): Boolean {
        try {
            if (src.isFile) {
                val `is` = FileInputStream(src)
                val op = FileOutputStream(tar)
                val bis = BufferedInputStream(`is`)
                val bos = BufferedOutputStream(op)
                val bt = ByteArray(1024 * 8)
                while (true) {
                    val len = bis.read(bt)
                    if (len == -1) {
                        break
                    } else {
                        bos.write(bt, 0, len)
                    }
                }
                bis.close()
                bos.close()
            } else if (src.isDirectory) {
                val files = src.listFiles()

                tar.mkdirs()
                for (file in files) {
                    copy(file.absoluteFile, File(tar.absoluteFile, file.name))
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }

    }

    /**
     * 移动文件或目录
     */
    fun move(src: String, tar: String): Boolean {
        return move(File(src), File(tar))
    }

    /**
     * 移动文件或目录
     */
    fun move(src: File, tar: File): Boolean {
        return rename(src, tar)
    }

    /**
     * 文件重命名
     */
    fun rename(oldPath: String, newPath: String): Boolean {
        return rename(File(oldPath), File(newPath))
    }

    /**
     * 文件重命名
     */
    fun rename(src: File, tar: File): Boolean {
        return src.renameTo(tar)
    }

    /**
     * 读取文本文件, 失败将返回空串
     */
    @JvmOverloads
    fun readText(filepath: String, charset: String = "utf-8"): String {
        try {
            val data = readBytes(filepath)
            if (data != null) {
                return String(data, Charset.forName(charset)).trim { it <= ' ' }
            }
        } catch (ignored: UnsupportedEncodingException) {
        }

        return ""
    }

    /**
     * 读取文件内容, 失败将返回空串
     */
    fun readBytes(filepath: String): ByteArray? {
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(filepath)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val len = fis.read(buffer, 0, buffer.size)
                if (len == -1) {
                    break
                } else {
                    baos.write(buffer, 0, len)
                }
            }
            val data = baos.toByteArray()
            baos.close()
            return data
        } catch (e: IOException) {
            return null
        } finally {
            closeSilently(fis)
        }
    }

    /**
     * 保存文本内容
     */
    @JvmOverloads
    fun writeText(filepath: String, content: String, charset: String = "utf-8"): Boolean {
        try {
            writeBytes(filepath, content.toByteArray(charset(charset)))
            return true
        } catch (e: UnsupportedEncodingException) {
            return false
        }

    }

    /**
     * 保存文件内容
     */
    fun writeBytes(filepath: String, data: ByteArray): Boolean {
        val file = File(filepath)
        var fos: FileOutputStream? = null
        try {
            if (!file.exists()) {

                file.parentFile.mkdirs()

                file.createNewFile()
            }
            fos = FileOutputStream(filepath)
            fos.write(data)
            return true
        } catch (e: IOException) {
            return false
        } finally {
            closeSilently(fos)
        }
    }

    /**
     * 追加文本内容
     */
    fun appendText(path: String, content: String): Boolean {
        val file = File(path)
        var writer: FileWriter? = null
        try {
            if (!file.exists()) {

                file.createNewFile()
            }
            writer = FileWriter(file, true)
            writer.write(content)
            return true
        } catch (e: IOException) {
            return false
        } finally {
            closeSilently(writer)
        }
    }

    /**
     * 获取文件大小
     */
    fun getLength(path: String): Long {
        val file = File(path)
        return if (!file.isFile || !file.exists()) {
            0
        } else file.length()
    }

    /**
     * 获取文件或网址的名称（包括后缀）
     */
    fun getName(pathOrUrl: String?): String {
        if (pathOrUrl == null) {
            return ""
        }
        val pos = pathOrUrl.lastIndexOf('/')
        return if (0 <= pos) {
            pathOrUrl.substring(pos + 1)
        } else {
            System.currentTimeMillis().toString() + "." + getExtension(pathOrUrl)
        }
    }

    /**
     * 获取文件名（不包括扩展名）
     */
    fun getNameExcludeExtension(path: String): String {
        try {
            var fileName = File(path).name
            val lastIndexOf = fileName.lastIndexOf(".")
            if (lastIndexOf != -1) {
                fileName = fileName.substring(0, lastIndexOf)
            }
            return fileName
        } catch (e: Exception) {
            return ""
        }

    }

    /**
     * 获取格式化后的文件大小
     */
    fun getSize(path: String): String {
        val fileSize = getLength(path)
        return ConvertUtils.toFileSizeString(fileSize)
    }

    /**
     * 获取文件后缀,不包括“.”
     */
    fun getExtension(pathOrUrl: String): String {
        val dotPos = pathOrUrl.lastIndexOf('.')
        return if (0 <= dotPos) {
            pathOrUrl.substring(dotPos + 1)
        } else {
            "ext"
        }
    }

    /**
     * 获取文件的MIME类型
     */
    fun getMimeType(pathOrUrl: String): String {
        val ext = getExtension(pathOrUrl)
        val map = MimeTypeMap.getSingleton()
        return map.getMimeTypeFromExtension(ext) ?: "*/*"
    }

    /**
     * 获取格式化后的文件/目录创建或最后修改时间
     */
    @JvmOverloads
    fun getDateTime(path: String, format: String = "yyyy年MM月dd日HH:mm"): String {
        val file = File(path)
        return getDateTime(file, format)
    }

    /**
     * 获取格式化后的文件/目录创建或最后修改时间
     */
    fun getDateTime(file: File, format: String): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = file.lastModified()
        return SimpleDateFormat(format, Locale.PRC).format(cal.time)
    }

    /**
     * 比较两个文件的最后修改时间
     */
    fun compareLastModified(path1: String, path2: String): Int {
        val stamp1 = File(path1).lastModified()
        val stamp2 = File(path2).lastModified()
        return if (stamp1 > stamp2) {
            1
        } else if (stamp1 < stamp2) {
            -1
        } else {
            0
        }
    }

    /**
     * 创建多级别的目录
     */
    fun makeDirs(path: String): Boolean {
        return makeDirs(File(path))
    }

    /**
     * 创建多级别的目录
     */
    fun makeDirs(file: File): Boolean {
        return file.mkdirs()
    }

    class SortByExtension : Comparator<File> {

        override fun compare(f1: File?, f2: File?): Int {
            return if (f1 == null || f2 == null) {
                if (f1 == null) {
                    -1
                } else {
                    1
                }
            } else {
                if (f1.isDirectory && f2.isFile) {
                    -1
                } else if (f1.isFile && f2.isDirectory) {
                    1
                } else {
                    f1.name.compareTo(f2.name, ignoreCase = true)
                }
            }
        }

    }

    class SortByName : Comparator<File> {
        private var caseSensitive: Boolean = false

        constructor(caseSensitive: Boolean) {
            this.caseSensitive = caseSensitive
        }

        constructor() {
            this.caseSensitive = false
        }

        override fun compare(f1: File?, f2: File?): Int {
            if (f1 == null || f2 == null) {
                return if (f1 == null) {
                    -1
                } else {
                    1
                }
            } else {
                return if (f1.isDirectory && f2.isFile) {
                    -1
                } else if (f1.isFile && f2.isDirectory) {
                    1
                } else {
                    val s1 = f1.name
                    val s2 = f2.name
                    if (caseSensitive) {
                        s1.compareTo(s2)
                    } else {
                        s1.compareTo(s2, ignoreCase = true)
                    }
                }
            }
        }

    }

    class SortBySize : Comparator<File> {

        override fun compare(f1: File?, f2: File?): Int {
            return if (f1 == null || f2 == null) {
                if (f1 == null) {
                    -1
                } else {
                    1
                }
            } else {
                if (f1.isDirectory && f2.isFile) {
                    -1
                } else if (f1.isFile && f2.isDirectory) {
                    1
                } else {
                    if (f1.length() < f2.length()) {
                        -1
                    } else {
                        1
                    }
                }
            }
        }

    }

    class SortByTime : Comparator<File> {

        override fun compare(f1: File?, f2: File?): Int {
            return if (f1 == null || f2 == null) {
                if (f1 == null) {
                    -1
                } else {
                    1
                }
            } else {
                if (f1.isDirectory && f2.isFile) {
                    -1
                } else if (f1.isFile && f2.isDirectory) {
                    1
                } else {
                    if (f1.lastModified() > f2.lastModified()) {
                        -1
                    } else {
                        1
                    }
                }
            }
        }

    }

}

