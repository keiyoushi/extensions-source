package keiyoushi.runtime.webviewpatch

/**
 * arm32 (Thumb-2) M120-M139 era: the same committed-mode X-Requested-With handler as [ModeLocator], built
 * for armeabi-v7a. The patch zeroes the committed-mode read (`ldr rN,[rB,#off]` -> `movs rN,#0`, or
 * `ldr.w` -> `mov.w rN,#0`), so the dispatch always takes the no-inject (NO_HEADER) branch.
 *
 * The anchor is the handler's own UMA histogram literal rather than the getter: on arm32 the handler reaches
 * the getter only through a long-branch veneer, and [ThumbLocator]'s getter scan misses some of these
 * builds. The literal is resolved per halfword (the nearest feeding `ldr rD,[pc,#]` or `movw`/`movt` in
 * the halfwords before each `add rD,pc`), so no linear-decode alignment is involved.
 *
 * Disjoint from [ThumbLocator] by requiring the settable-mode strings, which no build it claims carries,
 * and from every arm64 strategy by ELF class.
 */
internal class ThumbModeLocator(private val elf: Elf) {
    private val t = elf.text()
    private val taddr = t.addr
    private val tfoff = t.foff.toInt()
    private val nhw = (t.size / 2).toInt()

    private fun hwIdx(i: Int): Int = elf.hw(tfoff + i * 2)

    private fun hwAt(va: Long): Int = hwIdx(((va - taddr) / 2).toInt())

    private class Insn(val addr: Long, val size: Int, val hw1: Int, val hw2: Int)

    private fun decode(va: Long, n: Int): List<Insn> {
        val out = ArrayList<Insn>(n)
        var a = va
        while (out.size < n && (a - taddr) / 2 + 1 < nhw) {
            val h1 = hwAt(a)
            val ins = if (isThumb32(h1)) Insn(a, 4, h1, hwAt(a + 2)) else Insn(a, 2, h1, 0)
            out.add(ins)
            a += ins.size
        }
        return out
    }

    private fun isBl(i: Insn): Boolean = isThumbBl(i.size, i.hw1, i.hw2)

    /** `add rD,pc` sites whose nearest feeder in the [PIC_LOOKBACK] halfwords before them resolves to [target]. */
    private fun refsTo(target: Long): List<Long> {
        val out = ArrayList<Long>()
        for (ai in 0 until nhw) {
            val h = hwIdx(ai)
            if (!isThumbAddPc(h)) continue
            val rd = thumbAddPcRd(h)
            val pcBase = taddr + 2L * ai + 4
            var lit: Long? = null
            var movw: Int? = null
            var movt: Int? = null
            var k = ai - 1
            while (k >= maxOf(0, ai - PIC_LOOKBACK)) {
                val a = hwIdx(k)
                val b = if (k + 1 < nhw) hwIdx(k + 1) else 0
                val kva = taddr + 2L * k
                if (lit == null && rd < 8 && (a and LDR_LIT_T1_MASK) == LDR_LIT_T1 && ((a ushr 8) and 7) == rd) {
                    lit = elf.rdword(((kva + 4) and 3L.inv()) + (a and 0xFF) * 4L)
                }
                if (k + 1 < ai && thumbMovwtRd(b) == rd) {
                    if (movw == null && (a and MOVW_MASK) == MOVW) movw = thumbMovImm16(a, b)
                    if (movt == null && (a and MOVT_MASK) == MOVT) movt = thumbMovImm16(a, b)
                }
                k--
            }
            val viaLit = lit?.let { ((it + pcBase) and 0xFFFFFFFFL) == target } == true
            val viaMov = movw != null && movt != null && ((((movt.toLong() shl 16) or movw.toLong()) + pcBase) and 0xFFFFFFFFL) == target
            if (viaLit || viaMov) out.add(taddr + 2L * ai)
        }
        return out
    }

    /** Nearest `push {..,lr}` / `push.w {..,lr}` at or below [addr]. */
    private fun entryOf(addr: Long): Long? {
        val i = ((addr - taddr) / 2).toInt()
        var k = i
        while (k > maxOf(0, i - ENTRY_SCAN_BACK)) {
            val h = hwIdx(k)
            if (isThumbPushLrT1(h)) return taddr + 2L * k
            if (h == PUSH_LR_W_HW1 && k + 1 < nhw && (hwIdx(k + 1) and PUSH_LR_W_LR_BIT) != 0) return taddr + 2L * k
            k--
        }
        return null
    }

    private class Chain(val readVa: Long, val reg: Int, val width: Int)

    /**
     * From the handler entry: the first bl fed by `add r0,rB,#off` (off >= [MIN_HDR_OFF]) is HasHeader; it must
     * be followed by `cbz r0,T`; at T the committed-mode read `ldr rN,[rB,#mode]` (same rB, mode >=
     * [MIN_MODE_OFF]) must be followed by `cmp rN,#imm`; a `str` to that same [rB,#mode] (the commit) and then at
     * least one bl (the getter) must precede HasHeader. Anything else -> null.
     */
    private fun findChain(entry: Long, ref: Long): Chain? {
        val ins = decode(entry, CHAIN_WINDOW)
        // The histogram reference must sit in the decoded handler, so the chain belongs to the function it anchors.
        if (ins.isEmpty() || ref !in entry..ins.last().addr) return null
        for (k in ins.indices) {
            if (!isBl(ins[k])) continue
            var hdr: Pair<Int, Long>? = null
            var j = k - 1
            while (j >= maxOf(0, k - HDR_LOOKBACK)) {
                val r = thumbAddR0RnImm(ins[j].size, ins[j].hw1, ins[j].hw2)
                if (r != null && r.second >= MIN_HDR_OFF) {
                    hdr = r
                    break
                }
                j--
            }
            if (hdr == null) continue
            // The first HasHeader-shaped call decides; no later candidate is considered.
            val base = hdr.first
            val g = ins.getOrNull(k + 1) ?: return null
            val absent = thumbCbzR0Target(g.size, g.hw1, g.addr)?.takeIf { it > entry } ?: return null
            val at = decode(absent, 2)
            if (at.size < 2) return null
            val read = thumbLdrImm(at[0].size, at[0].hw1, at[0].hw2) ?: return null
            if (read.second != base || read.third < MIN_MODE_OFF) return null
            if (!isThumbCmpImmOn(at[1].size, at[1].hw1, read.first)) return null
            // A 32-bit patch must be one aligned word store, so no CPU can fetch half-old, half-new bytes.
            if (at[0].size == 4 && absent % 4 != 0L) return null
            val commit = (0 until k).firstOrNull { c ->
                thumbStrImm(ins[c].size, ins[c].hw1, ins[c].hw2)?.let { it.second == base && it.third == read.third } == true
            } ?: return null
            if ((commit + 1 until k).none { isBl(ins[it]) }) return null
            return Chain(absent, read.first, at[0].size)
        }
        return null
    }

    fun run(): LocateResult {
        if (!elf.containsAnyString(MODE_FEATURES)) {
            return LocateResult.noMatch("settable-mode feature strings absent -> not the mode era (arm32)")
        }
        val hist = elf.findTails(HISTOGRAM)
        if (hist.size != 1) return LocateResult.noMatch("committed-mode histogram literal not unique (arm32)")
        val refs = refsTo(hist[0])
        if (refs.size != 1) return LocateResult.noMatch("committed-mode histogram not referenced exactly once (arm32)")
        val entry = entryOf(refs[0]) ?: return LocateResult.noMatch("no entry for the committed-mode handler (arm32)")
        val chain = findChain(entry, refs[0]) ?: return LocateResult.noMatch("no committed-mode dispatch chain in the handler (arm32)")
        val foff = (t.foff + (chain.readVa - taddr)).toInt()
        // The 16-byte verify window is centred on the read (starts 8 bytes before it), as in the arm64 mode era.
        return LocateResult.emit(chain.readVa, elf.sliceHex(foff - 8, VERIFY_WINDOW), thumbZeroReg(chain.reg, chain.width), chain.readVa - 8)
    }

    private companion object {
        /** The same settable-mode strings the arm64 mode era gates on. */
        val MODE_FEATURES = listOf(
            "WebViewXRequestedWithHeaderControl",
            "WebViewXRequestedWithHeaderManifestAllowList",
            "RequestedWithHeader.CommittedHeaderMode",
        )

        /** The handler's UMA histogram; only the handler references it. */
        const val HISTOGRAM = "Android.WebView.RequestedWithHeader.CommittedHeaderMode"

        /** Halfwords searched before an `add rD,pc` for its feeding literal load or movw/movt. */
        const val PIC_LOOKBACK = 10

        /** Instructions decoded from the handler entry. */
        const val CHAIN_WINDOW = 96

        /** Instructions before a bl searched for the `add r0,rB,#off` that feeds it. */
        const val HDR_LOOKBACK = 6
    }
}
