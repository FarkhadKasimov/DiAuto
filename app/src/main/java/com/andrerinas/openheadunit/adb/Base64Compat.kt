package com.andrerinas.openheadunit.adb

/**
 * RFC 4648 Base64 with the semantics of the `java.util.Base64` basic and MIME codecs.
 */
object Base64Compat {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 3 <= bytes.size) {
            val bits = (bytes[index].toInt() and 0xff shl 16) or
                (bytes[index + 1].toInt() and 0xff shl 8) or
                (bytes[index + 2].toInt() and 0xff)
            out.append(ALPHABET[bits ushr 18 and 63]).append(ALPHABET[bits ushr 12 and 63])
                .append(ALPHABET[bits ushr 6 and 63]).append(ALPHABET[bits and 63])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val bits = bytes[index].toInt() and 0xff shl 16
                out.append(ALPHABET[bits ushr 18 and 63]).append(ALPHABET[bits ushr 12 and 63]).append("==")
            }
            2 -> {
                val bits = (bytes[index].toInt() and 0xff shl 16) or (bytes[index + 1].toInt() and 0xff shl 8)
                out.append(ALPHABET[bits ushr 18 and 63]).append(ALPHABET[bits ushr 12 and 63])
                    .append(ALPHABET[bits ushr 6 and 63]).append('=')
            }
        }
        return out.toString()
    }
}
