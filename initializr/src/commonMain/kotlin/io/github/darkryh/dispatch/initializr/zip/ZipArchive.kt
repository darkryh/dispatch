package io.github.darkryh.dispatch.initializr.zip

import io.github.darkryh.dispatch.initializr.template.TemplateFile

/**
 * A tiny, dependency-free ZIP writer using the STORED (no-compression) method. The generated project
 * is a handful of small text files, so compression buys nothing and STORED keeps the implementation
 * trivial and 100% Kotlin — no JS zip library, no native code.
 *
 * Produces a standard archive that `unzip`, Finder, Windows Explorer and Gradle all accept.
 */
object ZipArchive {
    // Fixed DOS timestamp (1980-01-01 00:00) — keeps output deterministic and avoids needing a clock.
    private const val DOS_TIME = 0
    private const val DOS_DATE = 0x21
    private const val UTF8_FLAG = 0x0800
    private const val VERSION = 20

    fun create(files: List<TemplateFile>): ByteArray {
        val out = ByteSink()
        val central = ByteSink()
        var entryCount = 0

        for (file in files) {
            val nameBytes = file.path.encodeToByteArray()
            val data = file.content.encodeToByteArray()
            val crc = Crc32.compute(data)
            val offset = out.size

            // ---- Local file header ----
            out.u32(0x04034B50)
            out.u16(VERSION)
            out.u16(UTF8_FLAG)
            out.u16(0) // compression: STORED
            out.u16(DOS_TIME)
            out.u16(DOS_DATE)
            out.u32(crc)
            out.u32(data.size.toLong()) // compressed size == uncompressed
            out.u32(data.size.toLong())
            out.u16(nameBytes.size)
            out.u16(0) // extra length
            out.raw(nameBytes)
            out.raw(data)

            // ---- Central directory header (buffered, appended after all entries) ----
            central.u32(0x02014B50)
            central.u16(VERSION) // version made by
            central.u16(VERSION) // version needed
            central.u16(UTF8_FLAG)
            central.u16(0) // compression
            central.u16(DOS_TIME)
            central.u16(DOS_DATE)
            central.u32(crc)
            central.u32(data.size.toLong())
            central.u32(data.size.toLong())
            central.u16(nameBytes.size)
            central.u16(0) // extra length
            central.u16(0) // comment length
            central.u16(0) // disk number start
            central.u16(0) // internal attrs
            central.u32(0) // external attrs
            central.u32(offset.toLong())
            central.raw(nameBytes)

            entryCount++
        }

        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.raw(centralBytes)

        // ---- End of central directory record ----
        out.u32(0x06054B50)
        out.u16(0) // this disk
        out.u16(0) // disk with central dir
        out.u16(entryCount)
        out.u16(entryCount)
        out.u32(centralBytes.size.toLong())
        out.u32(centralOffset.toLong())
        out.u16(0) // comment length

        return out.toByteArray()
    }
}

/** A minimal growable byte buffer with little-endian writers. */
private class ByteSink {
    private var buffer = ByteArray(1024)
    var size: Int = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var newCapacity = buffer.size
        while (newCapacity < size + extra) newCapacity *= 2
        buffer = buffer.copyOf(newCapacity)
    }

    fun byte(value: Int) {
        ensure(1)
        buffer[size++] = (value and 0xFF).toByte()
    }

    /** Little-endian unsigned 16-bit. */
    fun u16(value: Int) {
        byte(value)
        byte(value ushr 8)
    }

    /** Little-endian unsigned 32-bit (value carried in a Long to stay unsigned). */
    fun u32(value: Long) {
        byte((value and 0xFF).toInt())
        byte(((value ushr 8) and 0xFF).toInt())
        byte(((value ushr 16) and 0xFF).toInt())
        byte(((value ushr 24) and 0xFF).toInt())
    }

    fun u32(value: Int) = u32(value.toLong() and 0xFFFFFFFFL)

    fun raw(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
