package me.limeice.netlite

import java.io.File
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection

fun main(args: Array<String>) {
    val lite = RxNetLite.builder().create()

//    lite.get("https://fanyi.baidu.com")
//            .subscribe { bytes ->
//                println(String(bytes, Charset.forName("UTF-8")))
//            }
//
//    lite.get<HttpsURLConnection>("https://fanyi.baidu.com", { it.allowUserInteraction = true })
//            .subscribe { bytes ->
//                println(String(bytes, Charset.forName("UTF-8")))
//            }
    lite.download("https://fanyi.baidu.com", File("C:\\Users\\LimeV\\rxNetTest\\1.txt"), null)
            .subscribe(
                    {
                        println("下载${(it * 1000).toInt().toFloat() / 10}%")
                    },
                    {
                        println("下载异常")
                        it.printStackTrace()
                    },
                    {
                        println("下载完成")
                    }
            )

}

