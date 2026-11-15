package keiyoushi.runtime.webviewpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Keeps the pure-Kotlin XRW locator regression-tested inside the runtime module. The locator must reproduce the
 * Python oracle bit-for-bit on every corpus `.so` (both ABIs, every era), each build claimed by at
 * most one [LocateStrategy], and must fail closed on anything else.
 *
 * The corpus `.so` files are far too large to commit, so the full-matrix tests read them from the
 * directory named by `-Dxrw.corpus=<dir>` (skipped when unset); the oracle values themselves are
 * committed in `test-resources/xrw/oracle.tsv`. The fail-closed tests always run.
 */
class XrwLocatorTest {

    /**
     * The oracle is append-only. Each [LOCATOR_VERSION] pins the decisions of the rows that existed when it was
     * cut, so changing what an existing build gets needs a new version (and its pin) while a new build's row
     * needs none.
     */
    @Test
    fun existingDecisionsChangeOnlyWithANewVersion() {
        val (count, hash) = DECISION_PINS.getValue(LOCATOR_VERSION)
        val rows = oracleRows()
        assertTrue("oracle rows were removed", rows.size >= count)
        assertEquals("an existing build's decision changed: bump LOCATOR_VERSION and pin it", hash, decisionHash(rows.take(count)))
    }

    @Test
    fun failsClosedOnNonElf() {
        val inputs = listOf(
            ByteArray(0),
            "not an elf".toByteArray(),
            ByteArray(64),
            // Well-formed ELF shells: these get past Elf()'s magic check and reach the strategies, so the
            // returned-result half of the fail-safe path is covered — the three above only ever throw.
            junkElf(is64 = true),
            junkElf(is64 = false),
        )
        val outcomes = inputs.mapIndexed { i, bytes ->
            val r = runCatching { XrwLocator.locate(bytes) }.getOrNull()
            // An unrecognised input either throws or yields no patch site; never a spurious patch.
            if (r == null) {
                "threw"
            } else {
                assertNull("input $i: adder", r.adder)
                assertNull("input $i: verifyVa", r.verifyVa)
                assertNull("input $i: verifyHex", r.verifyHex)
                assertNull("input $i: patchHex", r.patchHex)
                "no-match"
            }
        }
        // Anti-vacuity guard: both shells must reach the returned no-match, or the assertions above never run.
        assertEquals(2, outcomes.count { it == "no-match" })
    }

    @Test
    fun oracleTsvIsWellFormed() {
        val rows = oracleRows()
        assertEquals("one row per build", rows.size, rows.map { it.sha }.toSet().size)
        rows.forEach { row ->
            if (row.adder == "-") {
                // A no-match row carries no window, no window VA, no patch, and no owning strategy.
                assertEquals("-", row.verify)
                assertEquals("-", row.verifyVa)
                assertEquals("-", row.patch)
                assertEquals("none", row.strategy)
            } else {
                // Every emitting row carries a 16-byte verify window, the VA that window starts at, and the
                // patch its strategy writes. Each strategy's window sits a fixed distance from its patch
                // site, so the era geometry (LocateResult.verifyVa) is locked here too.
                assertEquals(32, row.verify.length)
                val adderVa = row.adder.removePrefix("0x").toLong(16)
                val verifyOff = when (row.strategy) {
                    "out-of-line" -> {
                        assertEquals("c0035fd6", row.patch) // arm64 RET at the adder entry
                        0L
                    }
                    "inlined" -> {
                        assertEquals("1f2003d5", row.patch) // arm64 NOP; window ends at the SetHeader bl
                        -12L
                    }
                    "arm32" -> {
                        assertEquals("7047", row.patch) // Thumb bx lr at the adder entry
                        0L
                    }
                    // mode era: movz wN,#0 — the register varies per build, so assert the instruction, not fixed bytes.
                    "mode" -> {
                        assertEquals(8, row.patch.length)
                        val insn = row.patch.chunked(2).reversed().joinToString("").toLong(16) // little-endian word
                        assertEquals("${row.file}: not movz wN,#0", 0x52800000L, insn and 0xFFFFFFE0L)
                        -8L // window is centred on the committed-mode read
                    }
                    // arm32 mode era: movs rN,#0 (16-bit read) or mov.w rN,#0 (32-bit read); the register varies per build.
                    "arm32-mode" -> {
                        val hw = row.patch.chunked(4).map { it.chunked(2).reversed().joinToString("").toInt(16) } // little-endian halfwords
                        val zeroes = (hw.size == 1 && hw[0] and 0xF8FF == 0x2000) || (hw.size == 2 && hw[0] == 0xF04F && hw[1] and 0xF0FF == 0)
                        assertTrue("${row.file}: not movs/mov.w rN,#0", zeroes)
                        -8L // window is centred on the committed-mode read
                    }
                    // arm32 inlined era: a 16-bit unconditional b (T2) forcing the find-guard to skip the inject.
                    "arm32-inlined" -> {
                        val hw = row.patch.chunked(2).reversed().joinToString("").toInt(16) // little-endian halfword
                        assertEquals(4, row.patch.length)
                        assertEquals("${row.file}: not a 16-bit unconditional b", 0xE000, hw and 0xF800)
                        0L // the patch is the guard itself; the window starts at it
                    }
                    else -> error("emitting row ${row.file} has unknown strategy ${row.strategy}")
                }
                assertEquals("${row.file}: verifyVa", vaHex(adderVa + verifyOff), row.verifyVa)
            }
        }
    }

    @Test
    fun reproducesOracleOnCorpus() {
        val corpus = corpusDir() ?: return

        val divergences = oracleRows().mapNotNull { row ->
            val so = File(File(corpus, row.build), row.file)
            if (!so.isFile) return@mapNotNull "${row.build}/${row.file}: missing"
            val bytes = so.readBytes()
            val r = XrwLocator.locate(bytes)
            when {
                sha256Hex(bytes) != row.sha -> "${row.file}: sha ${sha256Hex(bytes)} != ${row.sha}"
                vaHex(r.adder) != row.adder -> "${row.file}: adder ${vaHex(r.adder)} != ${row.adder}"
                (r.verifyHex ?: "-") != row.verify -> "${row.file}: verify != oracle"
                vaHex(r.verifyVa) != row.verifyVa -> "${row.file}: verifyVa != oracle"
                (r.patchHex ?: "-") != row.patch -> "${row.file}: patch != oracle"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), divergences)
    }

    /**
     * Every strategy is tried against every corpus build. At most one may emit (disjointness), and the
     * one that does must be the era the oracle names, with its offset/verify/patch. A no-match build must
     * be claimed by none, so it falls back to the header.
     */
    @Test
    fun strategiesAreDisjointAndClaimTheOracleEra() {
        val corpus = corpusDir() ?: return

        val divergences = oracleRows().mapNotNull { row ->
            val so = File(File(corpus, row.build), row.file)
            if (!so.isFile) return@mapNotNull "${row.build}/${row.file}: missing"
            val bytes = so.readBytes()
            val elf = Elf(bytes)
            val emits = XrwLocator.strategies
                .mapNotNull { s -> s.locate(elf)?.takeIf { it.adder != null }?.let { s.id to it } }
            when {
                emits.size > 1 -> "${row.file}: ${emits.map { it.first }} both emit (not disjoint)"
                row.adder == "-" && emits.isNotEmpty() -> "${row.file}: expected no-match, got ${emits[0].first}"
                row.adder == "-" -> null
                emits.isEmpty() -> "${row.file}: expected ${row.strategy}, got no-match"
                emits[0].first != row.strategy -> "${row.file}: claimed by ${emits[0].first} != ${row.strategy}"
                vaHex(emits[0].second.adder) != row.adder -> "${row.file}: adder != oracle"
                (emits[0].second.verifyHex ?: "-") != row.verify -> "${row.file}: verify != oracle"
                vaHex(emits[0].second.verifyVa) != row.verifyVa -> "${row.file}: verifyVa != oracle"
                (emits[0].second.patchHex ?: "-") != row.patch -> "${row.file}: patch != oracle"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), divergences)
    }

    private fun corpusDir(): File? = System.getProperty("xrw.corpus")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        .also { assumeTrue("set -Dxrw.corpus=<dir> to run the full locator matrix", it != null) }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun decisionHash(rows: List<Row>): String =
        sha256Hex(rows.joinToString("\n") { listOf(it.sha, it.adder, it.verify, it.verifyVa, it.patch).joinToString("\t") }.toByteArray())

    /** A VA rendered the way the oracle records it, or "-" for a no-match. */
    private fun vaHex(va: Long?): String = va?.let { "0x" + it.toString(16) } ?: "-"

    /**
     * A well-formed but content-free ELF: magic and class, a section table holding one zero-filled
     * `.text` plus the `.shstrtab` [Elf] reads section names from, and no rodata-like section at all.
     * It parses, so every strategy runs against it; with no string section none of them can find the
     * X-Requested-With getter, so each no-matches and the locate falls through to its own no-match.
     */
    private fun junkElf(is64: Boolean): ByteArray {
        val text = ByteArray(64)
        val shstrtab = byteArrayOf(0) + ".text".toByteArray(Charsets.US_ASCII) + 0.toByte() +
            ".shstrtab".toByteArray(Charsets.US_ASCII) + 0.toByte()
        val textOff = if (is64) 64 else 52
        val shEnt = if (is64) 64 else 40
        val shstrOff = textOff + text.size
        val shOff = shstrOff + shstrtab.size
        val buf = ByteBuffer.allocate(shOff + 2 * shEnt).order(ByteOrder.LITTLE_ENDIAN)
        fun u8(at: Int, v: Int) = buf.put(at, v.toByte())
        fun u16(at: Int, v: Int) = buf.putShort(at, v.toShort())
        fun u32(at: Int, v: Long) = buf.putInt(at, v.toInt())
        fun u64(at: Int, v: Long) = buf.putLong(at, v)
        fun sec(at: Int, nameOff: Int, addr: Long, off: Long, size: Long) {
            u32(at + SH_NAME, nameOff.toLong())
            if (is64) {
                u64(at + SH_ADDR_64, addr)
                u64(at + SH_OFFSET_64, off)
                u64(at + SH_SIZE_64, size)
            } else {
                u32(at + SH_ADDR_32, addr)
                u32(at + SH_OFFSET_32, off)
                u32(at + SH_SIZE_32, size)
            }
        }

        // e_ident: ELF magic, class (ELFCLASS64 or 32-bit), LSB data encoding, version.
        u8(0, ELF_MAG0)
        u8(1, ELF_MAG1)
        u8(2, ELF_MAG2)
        u8(3, ELF_MAG3)
        u8(EI_CLASS, if (is64) ELFCLASS64 else 1)
        u8(5, 1) // EI_DATA = ELFDATA2LSB
        u8(6, 1) // EI_VERSION
        u16(16, 3) // e_type = ET_DYN
        u16(18, if (is64) 183 else 40) // e_machine = EM_AARCH64 / EM_ARM
        u32(20, 1L) // e_version
        if (is64) {
            u64(E_SHOFF_64, shOff.toLong())
            u16(E_SHENTSIZE_64, shEnt)
            u16(E_SHNUM_64, 2)
            u16(E_SHSTRNDX_64, 1)
        } else {
            u32(E_SHOFF_32, shOff.toLong())
            u16(E_SHENTSIZE_32, shEnt)
            u16(E_SHNUM_32, 2)
            u16(E_SHSTRNDX_32, 1)
        }
        sec(shOff, 1, 0x1000L, textOff.toLong(), text.size.toLong()) // ".text"
        sec(shOff + shEnt, 7, 0L, shstrOff.toLong(), shstrtab.size.toLong()) // ".shstrtab"
        buf.position(textOff)
        buf.put(text)
        buf.position(shstrOff)
        buf.put(shstrtab)
        return buf.array()
    }

    private data class Row(
        val build: String,
        val file: String,
        val sha: String,
        val adder: String,
        val verify: String,
        val verifyVa: String,
        val patch: String,
        val strategy: String,
    )

    private fun oracleRows(): List<Row> = javaClass.getResourceAsStream("/xrw/oracle.tsv")!!.bufferedReader().readLines()
        .filter { it.isNotBlank() }
        .map { it.split('\t') }
        // Column 2 is the corpus build's ABI: it documents the fixture, and the strategy column plus the
        // adder/patch geometry pin the same fact.
        .map { Row(it[0], it[1], it[3], it[4], it[5], it[6], it[7], it[8]) }
}

/** Per [LOCATOR_VERSION]: how many leading oracle rows it pins, and the hash of their decisions. */
private val DECISION_PINS = mapOf(
    1 to (57 to "3bc7b3745c628894abfee49ec8d98ab0bac20d3d66abc46498e250ea63b08046"),
)
