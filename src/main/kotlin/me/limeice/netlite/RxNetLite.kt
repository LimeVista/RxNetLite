package me.limeice.netlite

import io.reactivex.Observable
import me.limeice.netlite.internal.closeSilent
import me.limeice.netlite.internal.read
import java.io.InputStream
import java.io.OutputStream
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

open class RxNetLite() {

    companion object {
        const val DEFAULT_TIMEOUT: Int = 30000

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

    inner class Builder {

        fun setCaches(cache: DataCache) = apply { dataCache = cache }

        fun setConnectTimeout(timeOut: Int) = apply { connectTimeout = timeOut }

        fun setReadTimeout(timeOut: Int) = apply { readTimeout = timeOut }

        fun setInstanceFollowRedirects(boolean: Boolean) = apply { instanceFollowRedirects = boolean }

        fun setUserAgent(msg: String?) = apply { userAgent = msg }

        fun create(): RxNetLite = this@RxNetLite
    }

    /* Get操作 */
    fun get(url: String): Observable<ByteArray> {
        return Observable.just(url)
                .initGetConnection()
                .startGetConnection()
    }

    /* 携带预配置的 Get 操作*/
    fun <T : HttpURLConnection> get(url: String, config: (T) -> Unit): Observable<ByteArray> =
            Observable.just(url).map {
                @Suppress("UNCHECKED_CAST")
                val connect = URL(url).openConnection() as T
                initGetConnection(connect)
                config(connect)
                connect
            }.startGetConnection()

    /* 下载数据，最终回调下载数据长度 */
    fun download(url: String, out: OutputStream): Observable<Long> {
        return Observable.just(url)
                .initGetConnection()
                .startDownloadConnection(out)
    }

    /* 下载数据，带 0~1f 的下载进度 */
    fun <T : HttpURLConnection> download(url: String, out: OutputStream, config: (T) -> Unit): Observable<Float> {
        return Observable.create { e ->
            @Suppress("UNCHECKED_CAST") val connect = URL(url).openConnection() as T
            initGetConnection(connect)
            config(connect)
            val code = connect.responseCode
            val contentLength = connect.contentLengthLong
            if (code == 200) {
                var input: InputStream? = null
                try {
                    input = connect.inputStream
                    val buffer = ByteArray(1024)
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
                }
                e.onComplete()
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
        connect.doOutput = true
        connect.requestMethod = "GET"
        connect.instanceFollowRedirects = instanceFollowRedirects
        userAgent?.let { connect.setRequestProperty("User-Agent", userAgent) }
    }


    /* 过滤HTTPS操作*/
    private fun <T : HttpURLConnection> whenHttps(connect: T) {
        if (connect is HttpsURLConnection)
            setHttps(connect)
        connect.connect()
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
                }
            }
            throw RuntimeException("Network Connection Error!Error code: $code")
        }
    }

    private fun setHttps(connect: HttpsURLConnection) {
        // 暂时默认
    }
}