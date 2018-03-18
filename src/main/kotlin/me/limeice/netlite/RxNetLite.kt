package me.limeice.netlite

import io.reactivex.Observable
import me.limeice.netlite.internal.WrapEmitter
import me.limeice.netlite.internal.closeSilent
import me.limeice.netlite.internal.moveFile
import me.limeice.netlite.internal.read
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Suppress("MemberVisibilityCanBePrivate")
open class RxNetLite() {

    companion object {
        const val DEFAULT_TIMEOUT: Int = 30000

        internal const val CACHE_SIZE = 4096

        /* 免删除回调任务 */
        private const val TASK_NONE: Byte = 0

        /* 需要删除回调任务 */
        private const val TASK_NEED_CALL: Byte = 2

        /* 第一个任务（任务集合中没有重复任务） */
        private const val TASK_FIRST: Byte = 1

        @JvmStatic fun builder() = RxNetLite().Builder()
    }

    /* Get操作缓存处理类 */
    var dataCache: DataCache = DataCache()

    /* 连接操作，默认30秒*/
    var connectTimeout = DEFAULT_TIMEOUT
        set(value) {
            field = if (value < 0) DEFAULT_TIMEOUT else value
        }

    /* 读取超时，默认30秒*/
    var readTimeout = DEFAULT_TIMEOUT
        set(value) {
            field = if (value < 0) DEFAULT_TIMEOUT else value
        }

    /* 重定向 */
    var instanceFollowRedirects = true

    /* UserAgent */
    var userAgent: String? = "Mozilla/5.0 (Linux; Android 7.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.186 Mobile Safari/537.36"


    @Suppress("unused")
    inner class Builder internal constructor() {

        /* 设置缓存 */
        fun setCaches(cache: DataCache) = apply { dataCache = cache }

        /* 设置连接超时 */
        fun setConnectTimeout(timeOut: Int) = apply { connectTimeout = timeOut }

        /* 设置读取超时 */
        fun setReadTimeout(timeOut: Int) = apply { readTimeout = timeOut }

        /* 设置重定向 */
        fun setInstanceFollowRedirects(boolean: Boolean) = apply { instanceFollowRedirects = boolean }

        /* 设置浏览器头 */
        fun setUserAgent(msg: String?) = apply { userAgent = msg }

        /* 创建 */
        fun create(): RxNetLite = this@RxNetLite
    }

    /**
     * Get操作
     *
     * @param url URL
     *
     * @return RxJava 控制器，携带下载数据
     */
    fun get(url: String): Observable<ByteArray> {
        return Observable.just(url)
                .initGetConnection()
                .startGetConnection()
    }

    /**
     * Get操作
     *
     * @param url       URL
     * @param config    预配置
     *
     * @return RxJava 控制器，携带下载数据
     */
    fun <T : HttpURLConnection> get(url: String, config: (T) -> Unit): Observable<ByteArray> =
            Observable.just(url).map {
                @Suppress("UNCHECKED_CAST")
                val connect = URL(url).openConnection() as T
                initGetConnection(connect)
                config(connect)
                connect
            }.startGetConnection()

    /**
     *  下载数据
     *  @param url  URL
     *  @param out  输出流
     *
     *  @return RxJava 控制器（带有文件下载总长度）
     */
    fun download(url: String, out: OutputStream): Observable<Long> {
        return Observable.just(url)
                .initGetConnection()
                .startDownloadConnection(out)
    }

    /**
     *  下载数据，功能有限版本
     *
     *  @param url      URL
     *  @param outFile 下载到指定位置
     *
     *  @return RxJava 控制器（带有文件下载总长度）
     */
    fun download(url: String, outFile: File): Observable<Long> {
        val cache = dataCache.useDownloadCaches
        val out: FileOutputStream
        val cacheFile: File
        try {
            /* 设置缓存 */
            cacheFile = if (cache) {
                val f = dataCache.executeDownloadCacheFile(url)
                dataCache.createCacheFile(f)
                f
            } else outFile

            if (!cache && !outFile.exists()) outFile.createNewFile() // 如果文件不存在则创建
            out = FileOutputStream(cacheFile)
        } catch (e: Exception) {
            return Observable.fromCallable { throw e }
        }
        return Observable.just(url)
                .initGetConnection()
                .startDownloadConnection(out)
                .map { size ->
                    if (cache) {
                        if (outFile.exists()) outFile.delete() // 存在则删除
                        if (!cacheFile.moveFile(outFile)) {
                            cacheFile.delete()
                            throw RuntimeException("File download Error!,file path: ${outFile.absolutePath}")
                        }
                    }
                    size
                }
    }

    /**
     *  下载数据
     *
     *  @param url       URL
     *  @param outFile  下载到指定位置
     *  @param filter   任务过滤器
     *
     *  @return RxJava 控制器（带有0~1f的下载进度）
     */
    fun download(url: String, outFile: File, filter: DownloadFilter?): Observable<Float> =
            download<HttpURLConnection>(url, outFile, filter, { /*None*/ })

    /**
     *  下载数据(当该网站下载数据长度为-1时可以调用本函数尝试)
     *
     *  @param url       URL
     *  @param outFile  下载到指定位置
     *  @param filter   任务过滤器
     *
     *  @return RxJava 控制器（带有0~1f的下载进度）
     */
    fun downloadFixGetLength(url: String, outFile: File, filter: DownloadFilter?): Observable<Float> =
            download<HttpURLConnection>(
                    url, outFile, filter,
                    { it.addRequestProperty("Accept-Encoding", "identity") }
            )

    /**
     *  下载数据
     *
     *  @param url       URL
     *  @param outFile  下载到指定位置
     *  @param config   下载前对其配置
     *  @param filter   任务过滤器
     *
     *  @return RxJava 控制器（带有0~1f的下载进度）
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun <T : HttpURLConnection> download(url: String, outFile: File, filter: DownloadFilter?, config: (T) -> Unit): Observable<Float> {
        return Observable.create { e ->
            var tag: Byte = TASK_NONE // 标记任务

            /* 过滤器，任务调剂 */
            if (filter != null) {
                val emitter = filter.contains(url)
                if (emitter == null) {
                    tag = TASK_FIRST
                    filter.add(WrapEmitter(url))
                } else {
                    when (filter.filter) {
                        DownloadFilter.CANCEL -> return@create
                        DownloadFilter.OVERLAY -> Unit
                        DownloadFilter.WAIT_AFTER -> {
                            emitter.lock()
                            if (!emitter.error) e.onComplete()
                            return@create
                        }
                    }
                }
            }
            var input: InputStream? = null
            var out: FileOutputStream? = null
            val cache = dataCache.useDownloadCaches
            val cacheFile = if (cache) dataCache.executeDownloadCacheFile(url) else outFile // 是否开启缓存

            try {
                @Suppress("UNCHECKED_CAST") val connect = URL(url).openConnection() as T
                initGetConnection(connect)
                config(connect)
                connect.connect()
                val contentLength = connect.contentLengthLong
                val code = connect.responseCode
                if (code == 200) {
                    /* 创建缓存文件设置文件 */
                    if (cache) {
                        dataCache.createCacheFile(cacheFile)
                    } else if (!outFile.exists()) {
                        outFile.createNewFile()
                    }
                    input = connect.inputStream
                    out = FileOutputStream(cacheFile)
                    val buffer = ByteArray(CACHE_SIZE)
                    var count = 0L
                    // 循环读取
                    while (true) {
                        val len = input.read(buffer)
                        if (len == -1) break
                        count += len
                        out.write(buffer, 0, len)
                        e.onNext((count / contentLength.toDouble()).toFloat())
                    }
                    out.flush()
                    if (cache) { // 使用缓存
                        if (outFile.exists()) outFile.delete() // 当前存在则删除（覆盖）
                        if (cacheFile.moveFile(outFile))
                            e.onComplete() // 下载成功
                        else {
                            cacheFile.delete()
                            throw RuntimeException("File download Error!,file path: ${outFile.absolutePath}")
                        }
                    } else {
                        if (tag == TASK_FIRST) tag = TASK_NEED_CALL
                        e.onComplete() // 下载成功
                    }
                } else {
                    e.onError(RuntimeException("Network Connection Error!Error code: $code"))
                }
            } finally {
                input.closeSilent()
                out.closeSilent()
                if (cache) cacheFile.delete() // 下载失败删除缓存
                if (tag == TASK_NEED_CALL)
                    filter?.clearSuccess(url) // 完成任务
                else if (tag == TASK_FIRST) {
                    filter?.clearBreak(url) // 中断任务
                }
            }
        }
    }

    /* 初始化Get连接 */
    private fun Observable<String>.initGetConnection(): Observable<HttpURLConnection> {
        return map { url ->
            val connect = URL(url).openConnection() as HttpURLConnection
            initGetConnection(connect)
            connect
        }
    }

    /* 初始化Get连接 */
    private fun <T : HttpURLConnection> initGetConnection(connect: T) {
        whenHttps(connect)
        connect.readTimeout = readTimeout
        connect.connectTimeout = connectTimeout
        connect.useCaches = dataCache.useGetCaches
        connect.doInput = true
        // connect.doOutput = true
        connect.requestMethod = "GET"
        connect.instanceFollowRedirects = instanceFollowRedirects
        userAgent?.let { connect.setRequestProperty("User-Agent", userAgent) }
        // connect.connect() // 不需要显式调用
    }


    /* 过滤HTTPS操作*/
    private fun <T : HttpURLConnection> whenHttps(connect: T) {
        if (connect is HttpsURLConnection)
            setHttps(connect)
    }

    /* 连接到 [t] 获取数据,返回get操作数据 */
    private fun <T : HttpURLConnection> Observable<T>.startGetConnection(): Observable<ByteArray> {
        return map {
            val code = it.responseCode
            if (code == 200) {
                var input: InputStream? = null
                try {
                    input = it.inputStream
                    return@map read(input)
                } finally {
                    input.closeSilent()
                }
            }
            throw RuntimeException("Network Connection Error!Error code: $code")
        }
    }

    /* 连接到 [t] 获取数据,返回get操作数据 */
    private fun <T : HttpURLConnection> Observable<T>.startDownloadConnection(out: OutputStream): Observable<Long> {
        return map {
            val code = it.responseCode
            if (code == 200) {
                var input: InputStream? = null
                try {
                    input = it.inputStream
                    return@map read(input, out)
                } finally {
                    input.closeSilent()
                    it.disconnect()
                }
            }
            throw RuntimeException("Network Connection Error!Error code: $code")
        }
    }

    /* 暂时保留的方法 */
    @Suppress("UNUSED_PARAMETER")
    private fun setHttps(connect: HttpsURLConnection) {
        // 暂时默认
    }
}