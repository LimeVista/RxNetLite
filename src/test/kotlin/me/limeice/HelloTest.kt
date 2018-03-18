package me.limeice

import me.limeice.netlite.RxNetLite
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection
import kotlin.test.assertEquals

class HelloTest {

    private val lite = RxNetLite.builder().create()

    @Test
    fun download() {
        lite.download("http://c.hiphotos.baidu.com/image/pic/item/962bd40735fae6cd09ccfb7903b30f2442a70fa9.jpg", File("C:\\Users\\LimeV\\rxNetTest\\1.png"), null)
                .subscribe(
                        { println("下载${(it * 1000).toInt().toFloat() / 10}%") },
                        {
                            println("下载异常")
                            it.printStackTrace()
                        },
                        { println("下载完成") }
                )
    }

    @Test
    fun get() {
        var size1 = 0
        var size2 = 1
        lite.get("https://fanyi.baidu.com")
                .subscribe { bytes ->
                    println(String(bytes, Charset.forName("UTF-8")))
                    size1 = bytes.size
                }
        lite.get<HttpsURLConnection>("https://fanyi.baidu.com", { it.instanceFollowRedirects = true })
                .subscribe { size2 = it.size }
        assertEquals(size1, size2)
        println("数据长度:$size1")
    }
}
