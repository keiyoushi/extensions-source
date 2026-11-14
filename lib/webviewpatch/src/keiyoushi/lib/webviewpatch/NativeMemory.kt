package keiyoushi.lib.webviewpatch

import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Reads `/proc/self/maps` to find the mapped WebView `.so`, reads its bytes back from the file for
 * the locator, and performs copy-on-write self-patches through `/proc/self/mem`.
 *
 * Every write only touches this process's own copy-on-write pages of the shared WebView library.
 * No root, no other process, no other package's data.
 */
internal object NativeMemory {

    /** A mapped WebView library: where its ELF `vaddr 0` lives in memory, and the on-disk file to read it from. */
    data class MappedLib(
        /** Memory address where the ELF header (`.so` file offset 0) is mapped; symbol VA + this == live address. */
        val loadBase: Long,
        /** The backing file (a standalone `.so`, or the WebView apk the `.so` is embedded in). */
        val filePath: String,
        /** File offset of the ELF header within [filePath] (0 for a standalone `.so`, the apk offset otherwise). */
        val fileOffset: Long,
        /** "arm64" | "arm32", from the ELF header. */
        val abi: String,
    )

    private val LIB_NAMES = listOf("libwebviewchromium.so", "libmonochrome.so", "libmonochrome_64.so")

    // WebView provider package markers, to tell its apk from the app's own apk (both map r-xp embedded .so).
    private val PROVIDER_HINTS = listOf("webview", "trichrome", "chrome", "monochrome")

    private const val TAG = "XrwNativeMemory"

    private val WHITESPACE = Regex("""\s+""")

    /**
     * Finds the mapped WebView library. Scans `/proc/self/maps` for the mappings backed by a WebView
     * library file (a standalone `.so`, or an apk with the `.so` embedded), and for each such file reads
     * the ELF header + program headers back from the file to compute the load base.
     *
     * Important: the mapping holding the ELF header need NOT be executable. The usual lld layout puts the
     * ELF header in the first, READ-ONLY, `PT_LOAD` segment, with `.text` in a later executable segment at
     * a non-zero file offset (common on Trichrome). So all of the file's mappings are considered and the load
     * base is derived from the first `PT_LOAD` (`loadBase = <addr of file offset p_offset> - p_vaddr`), so a
     * symbol VA + loadBase is its live address regardless of which segment is executable. The file must still
     * have at least one executable mapping (it is the code library).
     */
    fun findWebViewLib(): MappedLib? = try {
        // Read through a stream, not RandomAccessFile: /proc files report size 0 and RandomAccessFile reads nothing.
        val maps = File("/proc/self/maps").bufferedReader().useLines { it.toList() }
            .mapNotNull(::parseMapLine)

        // Group the WebView-library mappings by backing file (a library can span several mappings).
        val byFile = maps
            .filter { m ->
                LIB_NAMES.any { m.path.endsWith(it) } ||
                    (m.path.endsWith(".apk") && PROVIDER_HINTS.any { m.path.contains(it, ignoreCase = true) })
            }
            .groupBy { it.path }

        byFile.entries.firstNotNullOfOrNull { (path, mappings) ->
            // The code library must be executable somewhere; the app's own non-code apk mappings are skipped.
            if (mappings.none { it.perms.contains('x') }) return@firstNotNullOfOrNull null
            resolveLib(path, mappings)
        }
    } catch (t: Throwable) {
        Log.e(TAG, "findWebViewLib", t)
        null
    }

    /**
     * Resolves a WebView library's load base from its ELF program headers. [mappings] are all of
     * [path]'s mappings; the `.so`'s ELF header sits at the file offset of the lowest-offset mapping whose
     * start reads back as an ELF header. The first `PT_LOAD` then fixes the load base.
     */
    private fun resolveLib(path: String, mappings: List<MapLine>): MappedLib? {
        // The mapping whose start holds the ELF header anchors the .so's file offset 0 (for an apk-embedded
        // .so, that offset is the apk offset of the entry). Try the lowest-offset mappings first.
        val header = mappings.sortedBy { it.offset }.firstOrNull { m ->
            readMem(m.start, ELF_MAGIC_LEN)?.let { it[0] == ELF_MAG0.toByte() && it[1] == ELF_MAG1.toByte() && it[2] == ELF_MAG2.toByte() && it[3] == ELF_MAG3.toByte() } == true
        } ?: return null

        return RandomAccessFile(path, "r").use { raf ->
            raf.seek(header.offset)
            val ehdr = ByteArray(ELF_HEADER_BYTES)
            raf.readFully(ehdr)
            val is64 = ehdr[EI_CLASS].toInt() == ELFCLASS64
            val machine = (ehdr[E_MACHINE].toInt() and 0xFF) or ((ehdr[E_MACHINE + 1].toInt() and 0xFF) shl 8)
            // EM_AARCH64 = 183, EM_ARM = 40, EM_X86_64 = 62, EM_386 = 3. The caller settles (not rescan)
            // on an "x86"/"other" ABI; the locators are ABI-gated and never claim one.
            val abi = when {
                is64 && machine == EM_AARCH64 -> "arm64"
                !is64 && machine == EM_ARM -> "arm32"
                is64 && machine == EM_X86_64 -> "x86_64"
                !is64 && machine == EM_386 -> "x86"
                else -> "other"
            }
            val firstLoadVaddr = firstLoadVaddr(raf, header.offset, ehdr, is64) ?: return null
            // header.start is the live address of the .so's file offset 0 (= vaddr firstLoadVaddr).
            MappedLib(loadBase = header.start - firstLoadVaddr, filePath = path, fileOffset = header.offset, abi = abi)
        }
    }

    /** The `p_vaddr` of the first `PT_LOAD` program header (0 in the usual layout), or null if unreadable. */
    private fun firstLoadVaddr(raf: RandomAccessFile, base: Long, ehdr: ByteArray, is64: Boolean): Long? {
        val phoff: Long
        val phentsize: Int
        val phnum: Int
        if (is64) {
            phoff = leU64(ehdr, E_PHOFF_64)
            phentsize = leU16(ehdr, E_PHENTSIZE_64)
            phnum = leU16(ehdr, E_PHNUM_64)
        } else {
            phoff = leU32(ehdr, E_PHOFF_32)
            phentsize = leU16(ehdr, E_PHENTSIZE_32)
            phnum = leU16(ehdr, E_PHNUM_32)
        }
        if (phnum <= 0 || phentsize <= 0) return null
        val ph = ByteArray(phentsize * phnum)
        raf.seek(base + phoff)
        raf.readFully(ph)
        for (i in 0 until phnum) {
            val o = i * phentsize
            val type = leU32(ph, o + P_TYPE).toInt()
            if (type != PT_LOAD) continue
            return if (is64) leU64(ph, o + P_VADDR_64) else leU32(ph, o + P_VADDR_32) // p_vaddr
        }
        return null
    }

    /**
     * A cheap identity for the mapped library: backing file path, the `.so`'s offset within it, and the
     * file's size + mtime. An installed apk/`.so` is immutable, and a provider update replaces the file
     * (new size/mtime), so this distinguishes builds without reading or hashing the ~150 MB library.
     */
    fun cheapKey(lib: MappedLib): String {
        val f = File(lib.filePath)
        return "${lib.filePath}:${lib.fileOffset}:${f.length()}:${f.lastModified()}"
    }

    /**
     * A read-only, off-heap view of the mapped WebView `.so` the locator reads through, plus its
     * sha. Backed by a `MappedByteBuffer` (page cache, not the Java heap), so the ~150 MB library
     * never becomes a single heap [ByteArray] that could OOM another thread (image decoding) on a
     * low-RAM phone. Offsets are the `.so`'s own file offsets. [close] releases the backing file;
     * the mapping stays valid until it is GC'd, so the caller drops it before the self-test.
     */
    internal class MappedSo(
        private val raf: RandomAccessFile,
        private val buffer: MappedByteBuffer,
    ) : ByteSource,
        Closeable {
        // Off-heap little-endian word view over the same mapped pages (a duplicate, never a heap copy),
        // so the hot .text word scan reads a word in one get() instead of four bounds-checked byte gets.
        private val words = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        override val size: Int get() = buffer.capacity()
        override fun u8(index: Int): Int = buffer.get(index).toInt() and 0xFF
        override fun slice(from: Int, len: Int): ByteArray {
            val out = ByteArray(len)
            val dup = buffer.duplicate()
            dup.position(from)
            dup.get(out)
            return out
        }

        // A 4-aligned index maps to word index/4; .text sits 16 KB-aligned in these builds, but a
        // non-aligned offset still reads correctly through the four-byte fallback.
        override fun u32le(index: Int): Long = if (index and 3 == 0) words.get(index ushr 2).toLong() and 0xFFFFFFFFL else u32leBytes(index)

        /** sha256 of the mapped span, fed to the digest straight from the buffer (no heap copy). */
        fun sha256Hex(): String = MessageDigest.getInstance("SHA-256")
            .apply { update(buffer.duplicate()) }
            .digest()
            .toHexString()

        override fun close() {
            runCatching { raf.close() }
        }
    }

    /**
     * Maps the WebView `.so` bytes the locator needs from [lib]'s backing file. Rather than the whole
     * file (~150 MB, with trailing symbol/exception/debug sections the locate never touches), it maps
     * only up to the furthest-needed section end — the section-header table, its name table, `.text`,
     * and the string sections (`.rodata` / `.data.rel.ro[.local]`). Offsets stay absolute, so [Elf]
     * reads the span identically whether the `.so` is standalone or embedded in an apk.
     */
    fun mapSo(lib: MappedLib): MappedSo? {
        var raf: RandomAccessFile? = null
        return try {
            raf = RandomAccessFile(lib.filePath, "r")
            raf.seek(lib.fileOffset)
            val head = ByteArray(ELF_HEADER_BYTES)
            raf.readFully(head)
            val span = neededSpan(raf, lib.fileOffset, head)
            require(span in 1..MAX_SO_BYTES) { "implausible .so span $span" }
            val buffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, lib.fileOffset, span)
            MappedSo(raf, buffer)
        } catch (t: Throwable) {
            runCatching { raf?.close() }
            Log.e(TAG, "mapSo", t)
            null
        }
    }

    private val NEEDED_SECTIONS = setOf(SEC_TEXT, SEC_RODATA, SEC_DATA_REL_RO, SEC_DATA_REL_RO_LOCAL)

    /**
     * The smallest prefix of the `.so` that covers every section the locator reads. Reads the
     * section-header table and its name table, then returns the furthest file-offset end among the
     * needed sections, the name table and the header table itself.
     */
    private fun neededSpan(raf: RandomAccessFile, base: Long, head: ByteArray): Long {
        val is64 = head[EI_CLASS].toInt() == ELFCLASS64
        val shoff: Long
        val shentsize: Int
        val shnum: Int
        val shstrndx: Int
        if (is64) {
            shoff = leU64(head, E_SHOFF_64)
            shentsize = leU16(head, E_SHENTSIZE_64)
            shnum = leU16(head, E_SHNUM_64)
            shstrndx = leU16(head, E_SHSTRNDX_64)
        } else {
            shoff = leU32(head, E_SHOFF_32)
            shentsize = leU16(head, E_SHENTSIZE_32)
            shnum = leU16(head, E_SHNUM_32)
            shstrndx = leU16(head, E_SHSTRNDX_32)
        }
        val tableEnd = shoff + shentsize.toLong() * shnum
        require(tableEnd in 1..MAX_SO_BYTES && shstrndx < shnum) { "bad section header table" }

        // Read the section headers (name offset, file offset, size), and the name table they index.
        val sh = ByteArray((shentsize.toLong() * shnum).toInt())
        raf.seek(base + shoff)
        raf.readFully(sh)
        fun foffOf(i: Int) = if (is64) leU64(sh, i * shentsize + SH_OFFSET_64) else leU32(sh, i * shentsize + SH_OFFSET_32)
        fun sizeOf(i: Int) = if (is64) leU64(sh, i * shentsize + SH_SIZE_64) else leU32(sh, i * shentsize + SH_SIZE_32)
        fun nameOffOf(i: Int) = leU32(sh, i * shentsize + SH_NAME).toInt()

        val strFoff = foffOf(shstrndx)
        val strSize = sizeOf(shstrndx)
        val names = ByteArray(strSize.toInt())
        raf.seek(base + strFoff)
        raf.readFully(names)
        fun nameAt(off: Int): String {
            var e = off
            while (e < names.size && names[e].toInt() != 0) e++
            return String(names, off, e - off, Charsets.US_ASCII)
        }

        var end = maxOf(tableEnd, strFoff + strSize, ELF_HEADER_BYTES.toLong())
        for (i in 0 until shnum) {
            if (nameAt(nameOffOf(i)) in NEEDED_SECTIONS) end = maxOf(end, foffOf(i) + sizeOf(i))
        }
        return end
    }

    /** Reads [len] bytes of our own process memory at [address], or null on failure. */
    fun readMem(address: Long, len: Int): ByteArray? = try {
        RandomAccessFile("/proc/self/mem", "r").use { raf ->
            raf.seek(address)
            val b = ByteArray(len)
            raf.readFully(b)
            b
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * Writes [bytes] at [address] in our own process memory through `/proc/self/mem` (copy-on-write).
     * Returns true only if a read-back equals [bytes]. Never throws.
     *
     * I-cache coherence: the write goes via the kernel's ptrace access path (`mem_write` ->
     * `access_remote_vm` -> `copy_to_user_page`), which on arm64 does I-cache maintenance for an executable
     * VMA — so no CPU keeps executing the stale cached instruction and no user-space clear/`IC IVAU`+`DSB`+`ISB`
     * is needed. A plain store from the thread's own page would NOT be coherent; this cross-mapping path is.
     *
     * Concurrent modification: each patch is a single 4-byte-aligned store, atomic on arm64 (no torn write).
     * The out-of-line `paciasp`->`ret` swap isn't on ARM's CMODX safe-to-modify-while-executing list (the
     * inlined `bl`->`nop` is), and the patch is process-wide, so another running WebView could execute the
     * adder mid-swap. Safety doesn't rest on ordering: the old and new words are each independently valid, so
     * a racing CPU runs one or the other (adds the header once, or returns early) — neither faults — and the
     * ptrace I-cache flush above leaves no CPU on the stale word.
     */
    fun writeMem(address: Long, bytes: ByteArray): Boolean = try {
        RandomAccessFile("/proc/self/mem", "rw").use { raf ->
            raf.seek(address)
            raf.write(bytes)
        }
        readMem(address, bytes.size)?.contentEquals(bytes) == true
    } catch (t: Throwable) {
        Log.e(TAG, "writeMem", t)
        false
    }

    private data class MapLine(val start: Long, val offset: Long, val perms: String, val path: String)

    private fun parseMapLine(line: String): MapLine? {
        // Format: <start>-<end> <perms> <offset> <dev> <inode> <pathname>
        val parts = line.trim().split(WHITESPACE, limit = 6)
        if (parts.size < 6) return null
        val range = parts[0].split('-')
        val start = range.getOrNull(0)?.toLongOrNull(16) ?: return null
        val perms = parts[1]
        val offset = parts[2].toLongOrNull(16) ?: return null
        val path = parts[5]
        if (path.isBlank() || path.startsWith('[')) return null
        return MapLine(start, offset, perms, path)
    }

    private fun leU16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun leU32(b: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 3 downTo 0) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    private fun leU64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 7 downTo 0) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    // ---- ELF numbers parsed here ----
    // NativeMemory does its own raw parse of the ELF header, program headers and section headers over
    // ByteArray heads. Magic/class/section constants come from Elf.kt (same package); the program-header
    // numbers below are read only here. Offsets are byte offsets; the _64/_32 suffix marks the layout.

    /** Bytes of the ELF header read before parsing its tables. */
    private const val ELF_HEADER_BYTES = 64

    /** Bytes compared against the ELF magic when sniffing a mapping's start. */
    private const val ELF_MAGIC_LEN = 4

    /** e_machine: the 16-bit architecture id, at this header offset. */
    private const val E_MACHINE = 0x12

    /** e_machine values the ABI probe distinguishes. */
    private const val EM_386 = 3
    private const val EM_ARM = 40
    private const val EM_X86_64 = 62
    private const val EM_AARCH64 = 183

    /** 64-bit ELF header: program-header table offset, entry size, entry count. */
    private const val E_PHOFF_64 = 0x20
    private const val E_PHENTSIZE_64 = 0x36
    private const val E_PHNUM_64 = 0x38

    /** 32-bit ELF header: the same three fields at their narrower offsets. */
    private const val E_PHOFF_32 = 0x1c
    private const val E_PHENTSIZE_32 = 0x2a
    private const val E_PHNUM_32 = 0x2c

    /** Program-header entry: p_type (segment kind) and p_vaddr (load VA); PT_LOAD is the loadable kind. */
    private const val P_TYPE = 0x00
    private const val P_VADDR_64 = 0x10
    private const val P_VADDR_32 = 0x08
    private const val PT_LOAD = 1

    /** Sanity cap on the mapped `.so` span and the section-header table end. */
    private const val MAX_SO_BYTES = 512L * 1024 * 1024
}
