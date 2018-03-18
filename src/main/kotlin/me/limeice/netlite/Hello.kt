package me.limeice.netlite

import java.nio.charset.Charset

fun main(args: Array<String>) {
    val lite = RxNetLite.builder().create()
    lite.get("https://fanyi.baidu.com")
            .subscribe { bytes ->
                println(String(bytes, Charset.forName("UTF-8")))
            }
}

