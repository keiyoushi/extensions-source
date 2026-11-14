package keiyoushi.lib.webviewpatch

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Read-only random-access byte view the locator reads the `.so` through. Backed by a heap [ByteArray]
 * in tests ([ArrayByteSource]) and by an off-heap `MappedByteBuffer` on device ([NativeMemory.mapSo]),
 * so the ~150 MB library is never pulled onto the Java heap. Offsets are absolute `.so` file offsets.
 */
internal interface ByteSource {
    val size: Int

    /** Unsigned byte (0..255) at [index]. */
    fun u8(index: Int): Int

    /** [len] bytes starting at [from], copied to a small heap array. */
    fun slice(from: Int, len: Int): ByteArray

    /**
     * 32-bit little-endian word (0..0xFFFFFFFF) at byte [index]. The hot `.text` word scan reads
     * through this; implementations serve a 4-aligned [index] from an off-heap word view and fall
     * back to the four [u8] reads otherwise. Bit-identical to assembling the four bytes little-endian.
     */
    fun u32le(index: Int): Long
}

/** Four-byte little-endian assembly, the fallback for a non-4-aligned [ByteSource.u32le]. */
internal fun ByteSource.u32leBytes(index: Int): Long = u8(index).toLong() or
    (u8(index + 1).toLong() shl 8) or
    (u8(index + 2).toLong() shl 16) or
    (u8(index + 3).toLong() shl 24)

/** A [ByteSource] over a heap [ByteArray] (tests, and the fail-closed non-ELF checks). */
internal class ArrayByteSource(private val bytes: ByteArray) : ByteSource {
    // A little-endian word view over the same array (small in tests); no extra heap copy of the bytes.
    private val words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    override val size: Int get() = bytes.size
    override fun u8(index: Int): Int = bytes[index].toInt() and 0xFF
    override fun slice(from: Int, len: Int): ByteArray = bytes.copyOfRange(from, from + len)
    override fun u32le(index: Int): Long = if (index and 3 == 0) words.get(index ushr 2).toLong() and 0xFFFFFFFFL else u32leBytes(index)
}

// ---- ELF format constants ----
// Named offsets and tags for the 32-/64-bit little-endian ARM ELF the locator parses. Offsets are in
// bytes; the _64/_32 suffix marks the layout they belong to. Field widths stay inline at the read site.

/** ELF magic: the first four header bytes, 0x7f 'E' 'L' 'F'. */
internal const val ELF_MAG0 = 0x7f
internal const val ELF_MAG1 = 0x45 // 'E'
internal const val ELF_MAG2 = 0x4c // 'L'
internal const val ELF_MAG3 = 0x46 // 'F'

/** e_ident[EI_CLASS] byte; value ELFCLASS64 marks a 64-bit object (else 32-bit). */
internal const val EI_CLASS = 4
internal const val ELFCLASS64 = 2

/** 64-bit ELF header: section-header table offset, entry size, entry count, section-name string-table index. */
internal const val E_SHOFF_64 = 0x28
internal const val E_SHENTSIZE_64 = 0x3a
internal const val E_SHNUM_64 = 0x3c
internal const val E_SHSTRNDX_64 = 0x3e

/** 32-bit ELF header: the same four fields at their narrower offsets. */
internal const val E_SHOFF_32 = 0x20
internal const val E_SHENTSIZE_32 = 0x2e
internal const val E_SHNUM_32 = 0x30
internal const val E_SHSTRNDX_32 = 0x32

/** Section-header entry: sh_name, the section-name string-table offset (same slot in both layouts). */
internal const val SH_NAME = 0x00

/** 64-bit section-header entry: sh_addr (load VA), sh_offset (file offset), sh_size. */
internal const val SH_ADDR_64 = 0x10
internal const val SH_OFFSET_64 = 0x18
internal const val SH_SIZE_64 = 0x20

/** 32-bit section-header entry: the same three fields at their narrower offsets. */
internal const val SH_ADDR_32 = 0x0c
internal const val SH_OFFSET_32 = 0x10
internal const val SH_SIZE_32 = 0x14

/** Section names the locate reads: the code section, and the rodata-like sections that hold C-string literals. */
internal const val SEC_TEXT = ".text"
internal const val SEC_RODATA = ".rodata"
internal const val SEC_DATA_REL_RO = ".data.rel.ro"
internal const val SEC_DATA_REL_RO_LOCAL = ".data.rel.ro.local"

/**
 * Minimal pure-Kotlin ELF reader for the XRW locator.
 *
 * Parses the section-header table of a 32- or 64-bit little-endian ARM ELF, exposes the
 * sections the locate needs (.text / .rodata / .data.rel.ro[.local]) and the primitives to
 * read words and scan strings. No Android, no native, no third-party deps.
 */
internal class Elf(private val src: ByteSource) {
    constructor(bytes: ByteArray) : this(ArrayByteSource(bytes))

    data class Sec(val nameOff: Int, val addr: Long, val foff: Long, val size: Long)

    val is64: Boolean
    val named: Map<String, Sec>

    private fun u8(o: Int): Int = src.u8(o)

    private fun u16(o: Int): Int = u8(o) or (u8(o + 1) shl 8)

    /** Public 16-bit LE read (Thumb halfwords). */
    fun hw(o: Int): Int = u16(o)

    fun u32(o: Int): Long = (u8(o).toLong()) or
        (u8(o + 1).toLong() shl 8) or
        (u8(o + 2).toLong() shl 16) or
        (u8(o + 3).toLong() shl 24)

    /** Fast 32-bit LE word at file offset [o] (off-heap word view); for the hot `.text` scan only. */
    fun u32le(o: Int): Long = src.u32le(o)

    private fun u64(o: Int): Long {
        var v = 0L
        for (k in 7 downTo 0) v = (v shl 8) or u8(o + k).toLong()
        return v
    }

    /** 16 bytes at file offset [foff], as a hex string (the locator's verify window). */
    fun sliceHex(foff: Int, len: Int): String = src.slice(foff, len).toHexString()

    init {
        require(
            src.size >= 4 && u8(0) == ELF_MAG0 &&
                u8(1) == ELF_MAG1 && u8(2) == ELF_MAG2 && u8(3) == ELF_MAG3,
        ) { "not ELF" }
        is64 = u8(EI_CLASS) == ELFCLASS64
        val eShoff: Long
        val es: Int
        val en: Int
        val ex: Int
        if (is64) {
            eShoff = u64(E_SHOFF_64)
            es = u16(E_SHENTSIZE_64)
            en = u16(E_SHNUM_64)
            ex = u16(E_SHSTRNDX_64)
        } else {
            eShoff = u32(E_SHOFF_32)
            es = u16(E_SHENTSIZE_32)
            en = u16(E_SHNUM_32)
            ex = u16(E_SHSTRNDX_32)
        }
        val secs = ArrayList<Sec>(en)
        for (i in 0 until en) {
            val off = (eShoff + i.toLong() * es).toInt()
            val name = u32(off + SH_NAME).toInt()
            val addr: Long
            val foff: Long
            val size: Long
            if (is64) {
                addr = u64(off + SH_ADDR_64)
                foff = u64(off + SH_OFFSET_64)
                size = u64(off + SH_SIZE_64)
            } else {
                addr = u32(off + SH_ADDR_32)
                foff = u32(off + SH_OFFSET_32)
                size = u32(off + SH_SIZE_32)
            }
            secs.add(Sec(name, addr, foff, size))
        }
        val sh = secs[ex].foff.toInt()
        fun nm(n: Int): String {
            var e = sh + n
            while (u8(e) != 0) e++
            return String(src.slice(sh + n, e - (sh + n)), Charsets.US_ASCII)
        }
        // Map name -> section; later sections win on a name clash, matching Python's dict build.
        val m = LinkedHashMap<String, Sec>()
        for (s in secs) m[nm(s.nameOff)] = s
        named = m
    }

    /** rodata-like sections that hold C-string literals, in Python's order. */
    fun strSections(): List<Sec> = listOf(SEC_RODATA, SEC_DATA_REL_RO, SEC_DATA_REL_RO_LOCAL)
        .mapNotNull { named[it] }.filter { it.size > 0 }

    private fun indexOf(needle: ByteArray, blobOff: Int, blobLen: Int, from: Int): Int {
        val end = blobOff + blobLen - needle.size
        var j = blobOff + from
        outer@ while (j <= end) {
            var k = 0
            while (k < needle.size) {
                if (u8(j + k) != (needle[k].toInt() and 0xFF)) {
                    j++
                    continue@outer
                }
                k++
            }
            return j - blobOff
        }
        return -1
    }

    /** VAs where `tok\0` occurs (a null-terminated string whose tail is tok). */
    fun findTails(tok: String): List<Long> {
        val nb = (tok + "\u0000").toByteArray(Charsets.US_ASCII)
        val res = ArrayList<Long>()
        for (s in strSections()) {
            var j = 0
            while (true) {
                val f = indexOf(nb, s.foff.toInt(), s.size.toInt(), j)
                if (f < 0) break
                res.add(s.addr + f)
                j = f + 1
            }
        }
        return res
    }

    /** VAs where tok is a full null-terminated c-string (null before and after). */
    fun findCstr(tok: String): List<Long> {
        val nb = tok.toByteArray(Charsets.US_ASCII)
        val res = ArrayList<Long>()
        for (s in strSections()) {
            val off = s.foff.toInt()
            val len = s.size.toInt()
            var j = 0
            while (true) {
                val f = indexOf(nb, off, len, j)
                if (f < 0) break
                val pre = f == 0 || u8(off + f - 1) == 0
                val post = (f + nb.size < len) && u8(off + f + nb.size) == 0
                if (pre && post) res.add(s.addr + f)
                j = f + 1
            }
        }
        return res
    }

    /** True if any of [toks] occurs as a raw substring of any string section (no null boundary). */
    fun containsAnyString(toks: List<String>): Boolean {
        for (s in strSections()) {
            val off = s.foff.toInt()
            val len = s.size.toInt()
            for (t in toks) {
                if (indexOf(t.toByteArray(Charsets.US_ASCII), off, len, 0) >= 0) return true
            }
        }
        return false
    }

    /** Read a 32-bit LE word at VA, from whichever loadable section contains it, or null. */
    fun rdword(va: Long): Long? {
        for (s in named.values) {
            if (s.size > 0 && va >= s.addr && va < s.addr + s.size) {
                return u32((s.foff + (va - s.addr)).toInt())
            }
        }
        return null
    }

    fun text(): Sec = named[SEC_TEXT] ?: error("no .text")

    private var arm64SearchCache: Arm64AdderSearch? = null

    /**
     * The arm64 search over this ELF, built once and shared by the three arm64 era strategies. The cache
     * is a field on this instance, so it lives and dies with one build: the registry hands every strategy
     * the same [Elf], and the oracle test builds a fresh [Elf] per corpus `.so`, so no getter or caller
     * list can leak across builds.
     */
    fun arm64Search(): Arm64AdderSearch = arm64SearchCache ?: Arm64AdderSearch(this).also { arm64SearchCache = it }
}
