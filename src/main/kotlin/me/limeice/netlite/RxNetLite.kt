package me.limeice.netlite

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
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
open class RxNetLite {

    companion object {
        const val DEFAULT_TIMEOUT: Int = 30000

        internal const val CACHE_SIZE = 4096

        /* 免删除回调任务 */
        private const val TASK_NONE: Byte = 0

        /* 需要删除回调任务 */
        private const val TASK_NEED_CALL: Byte = 2

        /* 第一个任务（任务集合中没有重复任务） */
        private const val TASK_FIRST: Byte = 1

        @JvmStatic
        fun builder() = RxNetLite().Builder()
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
                .map { startDownloadConnection(it, out) }
    }

    /**
     *  下载数据（可以结合RxJava流）（兼容Java语法）
     *
     *  @param url      URL
     *  @param outFile 下载到指定位置
     *  @param filter  下载过滤器
     *
     *  @return RxJava 控制器（带有文件）
     */
    fun download(url: String, outFile: File, filter: DownloadFilter) =
            download<HttpURLConnection>(url, outFile, filter, {})

    /**
     *  下载数据（可以结合RxJava流）（兼容Java语法）
     *
     *  @param url      URL
     *  @param outFile 下载到指定位置
     *
     *  @return RxJava 控制器（带有文件）
     */
    fun download(url: String, outFile: File) = download<HttpURLConnection>(url, outFile, null, {})


    /**
     *  下载数据（可以结合RxJava流）（兼容Java语法）
     *
     *  @param url      URL
     *  @param outFile 下载到指定位置
     *  @param filter  下载过滤器
     *  @param config  预置配置
     *
     *  @return RxJava 控制器（带有文件）
     */
    fun <T : HttpURLConnection> download(url: String, outFile: File, filter: DownloadFilter?, config: (T) -> Unit): Observable<File> {
        val cache = dataCache.useDownloadCaches
        return Observable.create {
            var tag: Byte = TASK_NONE // 标记任务
            var out: FileOutputStream? = null
            val cacheFile = if (cache) dataCache.executeDownloadCacheFile(url) else outFile
            try {
                /* 过滤器，任务调剂 */
                if (filter != null) {
                    val data = filter.checkout(url)
                    when (data.type) {
                        DownloadFilter.WrapData.TASK_NONE -> return@create
                        DownloadFilter.WrapData.TASK_FIRST -> tag = TASK_FIRST
                        DownloadFilter.WrapData.TASK_WAIT_AFTER -> {
                            data.emitter.lock()
                            if (!data.emitter.error) it.onNext(outFile)
                            return@create
                        }
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val connect = URL(url).openConnection() as T
                initGetConnection(connect)
                config(connect)
                cacheFile.checkFileExist(cache) // 检查文件状态
                out = FileOutputStream(cacheFile) // 开始流

                if (startDownloadConnection(connect, out, it) == Long.MIN_VALUE) return@create   // 下载错误则结束，抛异常

                if (cache) { // 使用缓存
                    if (outFile.exists()) outFile.delete() // 当前存在则删除（覆盖）
                    out.closeSilent()  // 防止文件移动失败
                    if (!cacheFile.moveFile(outFile)) {
                        cacheFile.delete()
                        it.onError(RuntimeException("File downloadProgress Error!,file path: ${outFile.absolutePath}"))
                        return@create
                    }
                }
                if (tag == TASK_FIRST) tag = TASK_NEED_CALL
                it.onNext(outFile) // 下载成功
            } finally {
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

    /**
     *  下载数据(带下载进度)
     *
     *  @param url       URL
     *  @param outFile  下载到指定位置
     *  @param filter   任务过滤器
     *
     *  @return RxJava 控制器（带有0~1f的下载进度）
     */
    fun downloadProgress(url: String, outFile: File, filter: DownloadFilter?): Observable<Float> =
            downloadProgress<HttpURLConnection>(url, outFile, filter, { /*None*/ })

    /**
     *  下载数据(带下载进度)(当该网站下载数据长度为-1时可以调用本函数尝试)
     *
     *  @param url       URL
     *  @param outFile  下载到指定位置
     *  @param filter   任务过滤器
     *
     *  @return RxJava 控制器（带有0~1f的下载进度）
     */
    fun downloadProgressFixGetLength(url: String, outFile: File, filter: DownloadFilter?): Observable<Float> =
            downloadProgress<HttpURLConnection>(
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
    fun <T : HttpURLConnection> downloadProgress(url: String, outFile: File, filter: DownloadFilter?, config: (T) -> Unit): Observable<Float> {
        return Observable.create { e ->
            var tag: Byte = TASK_NONE // 标记任务
            /* 过滤器，任务调剂 */
            if (filter != null) {
                val data = filter.checkout(url)
                when (data.type) {
                    DownloadFilter.WrapData.TASK_NONE -> return@create
                    DownloadFilter.WrapData.TASK_FIRST -> tag = TASK_FIRST
                    DownloadFilter.WrapData.TASK_WAIT_AFTER -> {
                        data.emitter.lock()
                        if (!data.emitter.error) e.onComplete()
                        return@create
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
                    cacheFile.checkFileExist(cache) // 创建缓存文件或检查文件是否存在
                    input = connect.inputStream     // 开始下载
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
                        out.closeSilent()  // 防止文件移动失败
                        if (!cacheFile.moveFile(outFile)) {
                            cacheFile.delete()
                            e.onError(RuntimeException("File downloadProgress Error!,File path: ${outFile.absolutePath},Cache File path:${cacheFile.absolutePath}"))
                            return@create
                        }
                    }
                    if (tag == TASK_FIRST) tag = TASK_NEED_CALL
                    e.onComplete() // 下载成功
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
    private fun <T : HttpURLConnection> startDownloadConnection(
            connect: T,                                 // Http通讯
            out: OutputStream,                          // 输出流
            emitter: ObservableEmitter<File>? = null    // 发射器
    ): Long {
        val code = connect.responseCode
        if (code == 200) {
            var input: InputStream? = null
            try {
                input = connect.inputStream
                return read(input, out)
            } finally {
                input.closeSilent()
                connect.disconnect()
            }
        }
        if (emitter != null)
            emitter.onError(RuntimeException("Network Connection Error!Error code: $code"))
        else
            throw RuntimeException("Network Connection Error!Error code: $code")
        return Long.MIN_VALUE
    }

    /* 检查文件下载是否使用缓存，如果使用缓存检查新建缓存文件，否则检查文件是否存在 */
    private fun File.checkFileExist(isUseCaches: Boolean) {
        if (isUseCaches) {
            dataCache.createCacheFile(this)
        } else if (!this.exists()) {
            this.createNewFile()
        }
    }

    /* 暂时保留的方法 */
    @Suppress("UNUSED_PARAMETER")
    private fun setHttps(connect: HttpsURLConnection) {
        // 暂时默认
    }
}