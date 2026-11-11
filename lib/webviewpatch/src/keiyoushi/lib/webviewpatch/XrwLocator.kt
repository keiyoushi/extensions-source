package keiyoushi.lib.webviewpatch

import keiyoushi.utils.decodeHex

/**
 * Pure-Kotlin structural locator for WebView's per-request X-Requested-With adder
 * (android_webview `AwProxyingURLLoaderFactory`).
 *
 * Takes a [ByteArray] (the mapped `.so`) and returns a [LocateResult]; it imports nothing
 * Android-specific and runs identically on the JVM and on device. Fail-closed: it emits only on a
 * unique, shape-verified site, else no-match.
 *
 * The per-era shape differs (out-of-line pure adder, inlined set-chain, arm32 Thumb), so the locate
 * is split into [LocateStrategy] implementations tried in order by [strategies]; see that list.
 */
internal data class LocateResult(
    val abi: String, // "arm64" | "arm32"
    val adder: Long?, // patch offset VA (adder entry for RET, SetHeader bl for NOP), or null (no-match)
    val confidence: String, // "high" | "medium" | "none"
    val verifyHex: String?, // the 16-byte verify window, or null
    val patchHex: String?, // patch bytes: arm64 RET c0035fd6 / NOP 1f2003d5 / movz wN,#0 (mode era) / Thumb bx lr 7047
    val verifyVa: Long?, // VA where the verify window begins (== adder for RET/Thumb, adder-12 for NOP, adder-8 for mode), or null
    val reason: String?, // no-match reason, or null on emit
) {
    val adderHex: String? get() = adder?.let { "0x" + it.toString(16) }

    companion object {
        /** No-match for [abi], carrying only the [reason] (no adder/window/patch). */
        fun noMatch(abi: String, reason: String): LocateResult = LocateResult(abi, null, "none", null, null, null, reason)

        /** An emit: a located adder with its verify window and patch bytes. */
        fun emit(
            abi: String,
            adder: Long,
            confidence: String,
            verifyHex: String,
            patchHex: String,
            verifyVa: Long,
        ): LocateResult = LocateResult(abi, adder, confidence, verifyHex, patchHex, verifyVa, null)
    }
}

/**
 * One WebView era's locate. Fail-closed: returns a [LocateResult] with a non-null [LocateResult.adder]
 * only when it is confident this build carries the shape it owns, else null (no-match). ABI-gated: a
 * strategy returns null immediately for an ABI it does not handle. Strategies are disjoint by
 * construction, so composing them in an ordered, append-only list never produces a wrong emit.
 */
internal interface LocateStrategy {
    val id: String
    fun locate(elf: Elf): LocateResult?
}

/**
 * arm64 M150+: the adder is a standalone pure function; patch is RET-at-entry (`paciasp` -> `ret`).
 * The `paciasp`->`ret` swap is not on ARM's concurrent-modify-and-execute (CMODX) safe list, but it is a
 * single 4-byte-aligned store whose old and new words are each valid and safe to execute, so a CPU racing
 * the store faults on neither; I-cache coherence is handled by the kernel ptrace write path (see
 * [NativeMemory.writeMem]).
 */
internal object Arm64OutOfLineStrategy : LocateStrategy {
    override val id = "out-of-line"
    override fun locate(elf: Elf): LocateResult? = if (!elf.is64) null else OutOfLineLocator(elf).run().takeIf { it.adder != null }
}

/**
 * arm64 M142/M146: the adder is inlined into the request-setup function (settable-mode API also gone);
 * patch NOPs the inner `SetHeader` bl. Disjoint from [Arm64OutOfLineStrategy] by requiring the
 * enclosing function to be NON-pure (the exact negation of the out-of-line gate, computed from the same
 * features), so no single function can satisfy both.
 */
internal object Arm64InlinedStrategy : LocateStrategy {
    override val id = "inlined"
    override fun locate(elf: Elf): LocateResult? = if (!elf.is64) null else InlinedLocator(elf).run().takeIf { it.adder != null }
}

/**
 * arm64 M110-M130: the X-Requested-With handler is a non-pure function that commits a requested-with mode
 * and dispatches on it from two mode-dependent SetHeader inject sites; patch forces the committed-mode read
 * (`ldr wN,[x19,#off]`) to `movz wN,#0`, so the dispatch always takes the no-inject (NO_HEADER) branch — the
 * same path the engine runs for a legitimately-NO_HEADER page. Disjoint from [Arm64InlinedStrategy] by
 * requiring the settable-mode feature strings to be PRESENT (gone in M142/M146), and from
 * [Arm64OutOfLineStrategy] because the mode function is non-pure.
 */
internal object Arm64ModeStrategy : LocateStrategy {
    override val id = "mode"
    override fun locate(elf: Elf): LocateResult? = if (!elf.is64) null else ModeLocator(elf).run().takeIf { it.adder != null }
}

/** arm32 (Thumb-2): standalone pure adder; patch is bx lr at entry. */
internal object Arm32Strategy : LocateStrategy {
    override val id = "arm32"
    override fun locate(elf: Elf): LocateResult? = if (elf.is64) null else ThumbLocator(elf).run().takeIf { it.adder != null }
}

internal object XrwLocator {
    /**
     * Ordered, append-only locate registry, newest era first. The first strategy to confidently emit
     * wins; because the strategies are disjoint, order only decides which no-match reason surfaces when
     * none emits, never which build gets claimed. Add a new era by appending a strategy — never by
     * editing an existing one — so a build that works today keeps working.
     */
    internal val strategies: List<LocateStrategy> = listOf(
        Arm64OutOfLineStrategy, // M150+
        Arm64InlinedStrategy, // M142/M146
        Arm64ModeStrategy, // M110-M130
        Arm32Strategy, // arm32
    )

    fun locate(bytes: ByteArray): LocateResult = locate(ArrayByteSource(bytes))

    fun locate(src: ByteSource): LocateResult {
        val elf = Elf(src)
        for (strategy in strategies) {
            strategy.locate(elf)?.let { return it }
        }
        return LocateResult.noMatch(if (elf.is64) "arm64" else "arm32", "no strategy matched")
    }

    fun hex2bytes(h: String): ByteArray = h.decodeHex()
}

internal fun ByteArray.sliceHex(off: Int, len: Int): String = copyOfRange(off, off + len).toHexString()
