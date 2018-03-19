package me.limeice.netlite

import java.io.File
import java.lang.RuntimeException

class DataCache() {

    /* 下载缓存文件夹路径 */
    var downloadCacheFolder: File? = null
        set(value) {
            field = value
            if (value == null)
                return
            if (!value.exists() && !value.mkdirs())
                throw RuntimeException("Unable to create directory.File path:${value.absolutePath}")
        }

    /* Get操作缓存 */
    var useGetCaches = true

    /* 下载文件缓存 */
    var useDownloadCaches = false
        set(value) {
            if (value && downloadCacheFolder == null)
                throw RuntimeException("Folder path cannot be empty!")
            field = value
        }

    /* 标识在退出JVM时清除下载错误的文件 */
    var onExitClearDownloadFile: Boolean = true

    /* 初始化并设置 [file]为下载文件缓存文件夹路径 */
    constructor(file: File) : this() {
        downloadCacheFolder = file
        useDownloadCaches = true
    }

    /* 通过[name]生成临时缓存文件夹*/
    fun executeDownloadCacheFile(url: String): File {
        val tag = if (url.length < 10) url else url.substring(url.length - 8, url.length)
        return File(downloadCacheFolder!!, "${url.hashCode()}_$tag.tmp")
    }

    /* 创建缓存文件  */
    fun createCacheFile(file: File) {
        if (file.exists())
            file.delete()
        file.createNewFile()
        if (onExitClearDownloadFile) file.deleteOnExit() // 退出前焚毁未下载完成文件
    }

    /* 清除缓存 */
    @Synchronized fun clearCache() {
        downloadCacheFolder?.delete()
        downloadCacheFolder?.mkdirs()
    }
}