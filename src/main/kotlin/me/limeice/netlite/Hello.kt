package me.limeice.netlite

import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection

fun main(args: Array<String>) {
    val lite = RxNetLite.builder().create()
    
    lite.get("https://fanyi.baidu.com")
            .subscribe { bytes ->
                println(String(bytes, Charset.forName("UTF-8")))
            }

    lite.get<HttpsURLConnection>("https://fanyi.baidu.com", { it.allowUserInteraction = true })
            .subscribe { bytes ->
                println(String(bytes, Charset.forName("UTF-8")))
            }

}

