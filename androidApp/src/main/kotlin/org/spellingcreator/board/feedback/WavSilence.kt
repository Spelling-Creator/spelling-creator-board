package org.spellingcreator.board.feedback

import java.io.ByteArrayOutputStream
import java.io.File

/** Adds silence to the front of a PCM WAV file, the format the text to speech engine writes. */
object WavSilence {
    /** Rewrites [file] with [millis] of silence before its audio. Returns false and leaves the file alone if it is not PCM WAV. */
    fun prepend(
        file: File,
        millis: Int,
    ): Boolean {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        if (bytes.size < 12 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return false

        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataStart = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = int(bytes, offset + 4)
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    channels = short(bytes, body + 2)
                    sampleRate = int(bytes, body + 4)
                    bitsPerSample = short(bytes, body + 14)
                }
                "data" -> {
                    dataStart = body
                    dataSize = size.coerceIn(0, bytes.size - body)
                }
            }
            if (dataStart >= 0) break
            offset = body + size + (size and 1)
        }
        if (dataStart < 0 || channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) return false

        val frameBytes = channels * bitsPerSample / 8
        val padBytes = (sampleRate.toLong() * millis / 1000).toInt() * frameBytes
        val out = ByteArrayOutputStream(bytes.size + padBytes)
        out.write(bytes, 0, dataStart)
        out.write(ByteArray(padBytes))
        out.write(bytes, dataStart, dataSize)
        val result = out.toByteArray()
        putInt(result, 4, result.size - 8)
        putInt(result, dataStart - 4, dataSize + padBytes)
        return runCatching { file.writeBytes(result) }.isSuccess
    }

    private fun ascii(
        bytes: ByteArray,
        at: Int,
    ): String = String(bytes, at, 4, Charsets.US_ASCII)

    private fun short(
        bytes: ByteArray,
        at: Int,
    ): Int = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun int(
        bytes: ByteArray,
        at: Int,
    ): Int = short(bytes, at) or (short(bytes, at + 2) shl 16)

    private fun putInt(
        bytes: ByteArray,
        at: Int,
        value: Int,
    ) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[at + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[at + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
