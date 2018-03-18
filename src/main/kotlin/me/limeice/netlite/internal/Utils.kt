@file:JvmName("RxUtils")

package me.limeice.netlite.internal

import io.reactivex.annotations.NonNull
import java.io.*


private const val CACHE_BUFFER = 1024 * 16 // 16K

fun Closeable?.closeSilent() {
    if (this == null) return
    try {
        close()
    } catch (ex: IOException) {
        // who cares
    }
}

/**
 * 从输入流中读取数据，并转换为Byte数组
 *
 * @param inStream 待操作的输入流
 * @return Byte数组形式的html文件
 * @throws IOException 各种异常，包括IOException
 */
@NonNull
@Throws(IOException::class)
fun read(@NonNull inStream: InputStream): ByteArray {
    // 字节缓冲流
    val outStream = ByteArrayOutputStream()
    try {
        val buffer = ByteArray(CACHE_BUFFER)
        // 循环读取
        while (true) {
            val len = inStream.read(buffer)
            if (len == -1) break
            outStream.write(buffer, 0, len)
        }
        return outStream.toByteArray()
    } finally {
        outStream.closeSilent()
    }
}

/**
 * 从输入流中读取数据，并转换为Byte数组
 *
 * @param inStream 待操作的输入流
 * @return 下载数据大小
 * @throws IOException 各种异常，包括IOException
 */
@Throws(IOException::class)
fun read(inStream: InputStream, outStream: OutputStream): Long {
    val buffer = ByteArray(CACHE_BUFFER)
    var count = 0L
    // 循环读取
    while (true) {
        val len = inStream.read(buffer)
        if (len == -1) break
        count += len
        outStream.write(buffer, 0, len)
    }
    outStream.flush()
    return count
}

/**
 * 移动文件
 *
 * @param output 输出文件
 * @return 是否移动成功{@code true}成功否则失败
 */
fun File.moveFile(output: File?): Boolean {
    return output != null && exists() && renameTo(output)
}