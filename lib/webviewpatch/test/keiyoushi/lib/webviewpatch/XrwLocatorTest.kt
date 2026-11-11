package keiyoushi.lib.webviewpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the pure-Kotlin XRW locator regression-tested inside core. The locator must reproduce the
 * Python oracle bit-for-bit on every corpus `.so` (both ABIs, every era), each build claimed by at
 * most one [LocateStrategy], and must fail closed on anything else.
 *
 * The corpus `.so` files are far too large to commit, so the full-matrix tests read them from the
 * directory named by `-Dxrw.corpus=<dir>` (skipped when unset); the oracle values themselves are
 * committed in `test-resources/xrw/oracle.tsv`. The fail-closed tests always run.
 */
class XrwLocatorTest {

    @Test
    fun failsClosedOnNonElf() {
        val reasons = listOf(ByteArray(0), "not an elf".toByteArray(), ByteArray(64))
        for (bytes in reasons) {
            val r = runCatching { XrwLocator.locate(bytes) }.getOrNull()
            // A non-ELF input either throws inside Elf() or yields no adder; never a spurious patch.
            if (r != null) assertNull(r.adder)
        }
    }

    @Test
    fun oracleTsvIsWellFormed() {
        val rows = oracleRows()
        assertEquals(25, rows.size)
        rows.forEach { row ->
            if (row.adder == "-") {
                // A no-match row carries no window, no patch, and no owning strategy.
                assertEquals("none", row.conf)
                assertEquals("-", row.verify)
                assertEquals("-", row.patch)
                assertEquals("none", row.strategy)
            } else {
                // Every emitting row carries a 16-byte verify window and the patch its strategy writes.
                assertEquals(32, row.verify.length)
                when (row.strategy) {
                    "out-of-line" -> assertEquals("c0035fd6", row.patch) // arm64 RET
                    "inlined" -> assertEquals("1f2003d5", row.patch) // arm64 NOP
                    "arm32" -> assertEquals("7047", row.patch) // Thumb bx lr
                    // mode era: movz wN,#0 — the register varies per build, so assert the instruction, not fixed bytes.
                    "mode" -> {
                        assertEquals(8, row.patch.length)
                        val insn = row.patch.chunked(2).reversed().joinToString("").toLong(16) // little-endian word
                        assertEquals("${row.file}: not movz wN,#0", 0x52800000L, insn and 0xFFFFFFE0L)
                    }
                    else -> error("emitting row ${row.file} has unknown strategy ${row.strategy}")
                }
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
                (r.adderHex ?: "-") != row.adder -> "${row.file}: adder ${r.adderHex} != ${row.adder}"
                r.confidence != row.conf -> "${row.file}: conf ${r.confidence} != ${row.conf}"
                (r.verifyHex ?: "-") != row.verify -> "${row.file}: verify != oracle"
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
                (emits[0].second.adderHex ?: "-") != row.adder -> "${row.file}: adder != oracle"
                (emits[0].second.verifyHex ?: "-") != row.verify -> "${row.file}: verify != oracle"
                (emits[0].second.patchHex ?: "-") != row.patch -> "${row.file}: patch != oracle"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), divergences)
    }

    private fun corpusDir(): File? = System.getProperty("xrw.corpus")?.let(::File)?.takeIf { it.isDirectory }
        .also { assumeTrue("set -Dxrw.corpus=<dir> to run the full locator matrix", it != null) }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Row(
        val build: String,
        val file: String,
        val abi: String,
        val sha: String,
        val adder: String,
        val conf: String,
        val verify: String,
        val patch: String,
        val strategy: String,
    )

    private fun oracleRows(): List<Row> = javaClass.getResourceAsStream("/xrw/oracle.tsv")!!.bufferedReader().readLines()
        .filter { it.isNotBlank() }
        .map { it.split('\t') }
        .map { Row(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8]) }
}
