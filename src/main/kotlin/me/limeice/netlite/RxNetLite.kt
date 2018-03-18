package me.limeice.netlite

import io.reactivex.Observable
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

open class RxNetLite() {

    companion object {
        const val DEFAULT_TIMEOUT: Int = 30000

        internal const val CACHE_SIZE = 4096

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
     *  下载数据
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
            cacheFile = if (cache) dataCache.executeDownloadCacheFile(url) else outFile
            out = FileOutputStream(cacheFile)
        } catch (e: Exception) {
            return Observable.fromCallable { throw e }
        }
        return Observable.just(url)
                .initGetConnection()
                .startDownloadConnection(out)
                .map { size ->
                    if (cache)
                        if (!cacheFile.moveFile(outFile)) {
                            cacheFile.delete()
                            throw RuntimeException("File download Error!,file path: ${outFile.absolutePath}")
                        }
                    size
                }
    }

    /**
     *  下载数据
     *
     *  @param url       URL
     *  @param outFile  下载到指定位置
     *  @param config   下载前对其配置
     *
     *  @return RxJava 控制器（带有0~1f的下载进度）
     */
    fun <T : HttpURLConnection> download(url: String, outFile: File, config: (T) -> Unit): Observable<Float> {
        return Observable.create { e ->
            @Suppress("UNCHECKED_CAST") val connect = URL(url).openConnection() as T
            initGetConnection(connect)
            config(connect)
            val code = connect.responseCode
            val contentLength = connect.contentLengthLong
            if (code == 200) {
                var input: InputStream? = null
                var out: FileOutputStream? = null
                val cache = dataCache.useDownloadCaches

                val cacheFile = if (cache) dataCache.executeDownloadCacheFile(url) else outFile // 是否开启缓存

                try {
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
                } finally {
                    input.closeSilent()
                    out.closeSilent()
                }
                if (cache) { // 使用缓存
                    if (cacheFile.moveFile(outFile))
                        e.onComplete() // 下载成功
                    else {
                        cacheFile.delete()
                        throw RuntimeException("File download Error!,file path: ${outFile.absolutePath}")
                    }
                } else {
                    e.onComplete() // 下载成功
                }
            }
            throw RuntimeException("Network Connection Error!Error code: $code")
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

    private fun setHttps(connect: HttpsURLConnection) {
        // 暂时默认
    }
}