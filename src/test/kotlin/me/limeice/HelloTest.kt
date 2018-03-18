package me.limeice

import io.reactivex.schedulers.Schedulers
import me.limeice.netlite.DownloadFilter
import me.limeice.netlite.RxNetLite
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection
import kotlin.test.assertEquals

class HelloTest {

    private val lite = RxNetLite.builder().create()

    private val folder = System.getProperty("java.io.tmpdir")

    @Test
    fun download() {
        val filter = DownloadFilter()

        /* 如果该任务已经存在，阻塞当前任务，当之前任务完成时和之前任务一样，执行后续操作 */
        filter.filter = DownloadFilter.WAIT_AFTER
        var i = 0
        while (i++ < 18) {
            lite.download("http://c.hiphotos.baidu.com/image/pic/item/962bd40735fae6cd09ccfb7903b30f2442a70fa9.jpg",
                    File("$folder${File.separator}133.png"), filter)
                    .subscribeOn(Schedulers.io())           // IO线程执行
                    .observeOn(Schedulers.computation())    // 计算线程完成显示
                    .subscribe(
                            { println("下载${(it * 1000).toInt().toFloat() / 10}%") },
                            {
                                println("下载异常")
                                it.printStackTrace()
                            },
                            { println("下载完成队列：${Thread.currentThread().name}") }
                    )
        }
        Thread.sleep(10000)
    }

    @Test
    fun get() {
        var size1 = 0
        var size2 = 1
        // Get 操作
        lite.get("https://fanyi.baidu.com")
                .subscribe { bytes ->
                    println(String(bytes, Charset.forName("UTF-8"))) // 打印结果
                    size1 = bytes.size
                }
        // Get 操作
        lite.get<HttpsURLConnection>("https://fanyi.baidu.com", { it.instanceFollowRedirects = true })
                .subscribe { size2 = it.size }
        assertEquals(size1, size2)
        println("数据长度:$size1")
    }

    @Test
    fun downloadLite() {
        val file = File("$folder${File.separator}233.png")
        file.createNewFile()
        file.deleteOnExit()
        lite.download("http://c.hiphotos.baidu.com/image/pic/item/962bd40735fae6cd09ccfb7903b30f2442a70fa9.jpg",
                file)
                .subscribeOn(Schedulers.trampoline()) // 下载在IO线程
                .observeOn(Schedulers.computation()) // 显示消息在另外线程
                .subscribe { println("数据长度1:$it, 线程${Thread.currentThread().name}") }

        val file2 = File("$folder${File.separator}333.png")
        file2.createNewFile()

        lite.download("http://c.hiphotos.baidu.com/image/pic/item/962bd40735fae6cd09ccfb7903b30f2442a70fa9.jpg",
                file2.outputStream())
                .subscribeOn(Schedulers.io()) // 下载在IO线程
                .observeOn(Schedulers.newThread()) // 显示消息在另外线程
                .subscribe {
                    println("数据长度2:$it, 线程${Thread.currentThread().name}")
                    file2.delete()
                }
        Thread.sleep(3000)
    }
}
