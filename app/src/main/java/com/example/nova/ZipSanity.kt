package com.example.nova.util

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

object ZipSanity {
    private const val TAG = "ZipSanity"

    private fun hex(bytes: ByteArray, n: Int = bytes.size): String {
        val sb = StringBuilder()
        for (i in 0 until kotlin.math.min(n, bytes.size)) {
            sb.append(String.format("%02X", bytes[i])).append(' ')
        }
        return sb.toString().trim()
    }

    private fun firstBytes(path: String, count: Int = 8): ByteArray {
        val f = File(path)
        val buf = ByteArray(count)
        FileInputStream(f).use { fis ->
            val read = fis.read(buf)
            if (read < count) return buf.copyOf(read.coerceAtLeast(0))
        }
        return buf
    }

    fun isZip(path: String): Boolean {
        val b = firstBytes(path, 4)
        return b.size >= 4 &&
                b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
                b[2] == 0x03.toByte() && b[3] == 0x04.toByte()
    }

    /** Debug helper – no longer used for validation */
    fun sniff(path: String, limit: Int = 20): String {
        val f = File(path)
        if (!f.exists()) return "NOFILE: $path"
        val magic = hex(firstBytes(path, 8))
        val header = if (isZip(path)) "ZIP" else "NOT_ZIP"
        val sizeMb = String.format("%.1f", f.length() / 1024.0 / 1024.0)

        val sb = StringBuilder("[$header] size=${sizeMb}MB magic=$magic\n")
        if (header == "ZIP") {
            try {
                ZipFile(f).use { z ->
                    val names = z.entries().toList().map { it.name }.take(limit)
                    for (n in names) sb.append(" - ").append(n).append('\n')
                }
            } catch (e: Exception) {
                sb.append(" <Zip open error: ").append(e.message).append(">\n")
            }
        }
        val out = sb.toString().trimEnd()
        Log.d(TAG, out)
        return out
    }
}
