package keiyoushi.runtime.webviewpatch

/**
 * arm32 (Thumb-2) M142/M146 era: the committed-mode API is gone and the adder is inlined into the
 * request-setup function — the armeabi-v7a counterpart of [InlinedLocator]. The getter is resolvable on
 * these builds, so this anchors on the getter's bl callers, like [InlinedLocator] does on arm64.
 *
 * Shape (byte-identical M142/M146 arm32): getter -> `add r0,rB,#HDR` (request.headers) -> find bl ->
 * `cbnz r0,SKIP` guard -> inject block (`host_package_name()`, `add r0,rB,#SET` cors-exempt map, SetHeader
 * bl) -> continuation at SKIP. Both header and map are members at `rB+off`.
 *
 * Patch: force the guard to always skip the inject. [InlinedLocator] NOPs the SetHeader bl, but on arm32
 * that bl is a 32-bit BL at a 2-but-not-4-aligned address, so a 4-byte `nop.w` over it straddles two
 * aligned words — not a single aligned store, unsafe to modify while a peer WebView may execute it. The
 * 16-bit `cbnz r0,SKIP` is instead rewritten to an unconditional 16-bit `b SKIP` (one aligned 2-byte
 * store, widened to its word by [NativeMemory.writeMem]). The function then always takes the
 * header-present path it already runs: no inject, the getter's std::string name still destructs on the
 * shared epilogue, `host_package_name()`'s cached-member reference is simply unused. Only `cbnz`
 * (branch-when-present) is accepted, so forcing it unconditional always means "skip the inject"; a `cbz`
 * would invert the meaning and no-matches.
 *
 * Disjoint from [Arm32Strategy] (out-of-line, M150+) by the exact negation of its gate — the enclosing
 * function must be NON-pure (carry the `mvn #imm` error sentinel before its first ret), which a pure
 * standalone adder never does; from [Arm32ModeStrategy] by requiring the settable-mode strings ABSENT; and
 * from every arm64 strategy by ELF class.
 */
internal class ThumbInlinedLocator(private val elf: Elf) {
    private val t = elf.text()
    private val taddr = t.addr
    private val tfoff = t.foff.toInt()
    private val nhw = (t.size / 2).toInt()

    private fun hwIdx(i: Int): Int = elf.hw(tfoff + i * 2)

    private class Insn(val addr: Long, val size: Int, val hw1: Int, val hw2: Int)

    private fun decode(va: Long, n: Int): List<Insn> {
        val out = ArrayList<Insn>(n)
        var a = va
        while (out.size < n && (a - taddr) / 2 + 1 < nhw) {
            val h1 = hwIdx(((a - taddr) / 2).toInt())
            val ins = if (isThumb32(h1)) Insn(a, 4, h1, hwIdx(((a - taddr) / 2).toInt() + 1)) else Insn(a, 2, h1, 0)
            out.add(ins)
            a += ins.size
        }
        return out
    }

    private fun isBl(i: Insn): Boolean = isThumbBl(i.size, i.hw1, i.hw2)

    // ---- getter (PIC Requested/With resolve; same idiom as ThumbLocator, kept self-contained) ----

    private fun resolvedVaAt(ai: Int): Long? {
        val h = hwIdx(ai)
        if (!isThumbAddPc(h)) return null
        val aAddr = taddr + 2L * ai
        val rdn = thumbAddPcRd(h)
        var movw: Int? = null
        var movt: Int? = null
        var lit: Long? = null
        var k = ai - 1
        while (k >= maxOf(0, ai - PIC_LOOKBACK)) {
            val x = decode(taddr + 2L * k, 1)[0]
            thumbMovwImm16(x.size, x.hw1, x.hw2)?.let { if (thumbMovwtRd(x.hw2) == rdn) movw = it }
            thumbMovtImm16(x.size, x.hw1, x.hw2)?.let { if (thumbMovwtRd(x.hw2) == rdn) movt = it }
            thumbLdrLiteralAddr(x.size, x.hw1, x.hw2, x.addr)?.let { (rt, a) -> if (rt == rdn) elf.rdword(a)?.let { lit = it } }
            k--
        }
        val delta: Long = when {
            movw != null && movt != null -> ((movt.toLong() shl 16) or movw.toLong())
            lit != null -> lit
            else -> return null
        }
        return (delta + aAddr + 4) and 0xFFFFFFFFL
    }

    private fun entryOf(addr: Long): Long? {
        var i = ((addr - taddr) / 2).toInt()
        while (i > maxOf(0, ((addr - taddr) / 2).toInt() - ENTRY_SCAN_BACK)) {
            val h = hwIdx(i)
            if (isThumbPushLrT1(h)) return taddr + 2L * i
            if (h == PUSH_LR_W_HW1 && i + 1 < nhw && (hwIdx(i + 1) and PUSH_LR_W_LR_BIT) != 0) return taddr + 2L * i
            i--
        }
        return null
    }

    private fun findGetter(): Long? {
        val rq = elf.findTails("Requested").toHashSet()
        val wi = elf.findTails("With").toHashSet()
        if (rq.isEmpty() || wi.isEmpty()) return null
        val targets = rq + wi
        val byRq = HashMap<Long, Boolean>()
        val byWi = HashMap<Long, Boolean>()
        for (ai in 0 until nhw) {
            val va = resolvedVaAt(ai) ?: continue
            if (va !in targets) continue
            val e = entryOf(taddr + 2L * ai) ?: continue
            if (va in rq) byRq[e] = true
            if (va in wi) byWi[e] = true
        }
        val cands = byRq.keys.filter { byWi.containsKey(it) }
        return if (cands.size == 1) cands[0] else null
    }

    private fun blCallers(target: Long): List<Long> {
        val out = ArrayList<Long>()
        val tgt = target and 0xFFFFFFFEL
        for (i in 0 until nhw - 1) {
            val pc = taddr + 2L * i
            if (thumbBlTarget(hwIdx(i), hwIdx(i + 1), pc) == tgt) out.add(pc)
        }
        return out
    }

    // ---- the inlined set-chain ----

    private class Chain(val guardVa: Long, val target: Long)

    private fun findChain(entry: Long, getter: Long): Chain? {
        val ins = decode(entry, CHAIN_WINDOW)
        val gi = ins.indexOfFirst { isBl(it) && thumbBlTarget(it.hw1, it.hw2, it.addr) == (getter and 0xFFFFFFFEL) }
        if (gi < 0) return null
        // Non-pure pivot (exact negation of the arm32 out-of-line gate): a `mvn #imm` error sentinel must
        // appear before the first ret. A pure standalone adder has none, so the two strategies can never
        // claim one function. Mirrors how InlinedLocator negates the out-of-line gate on arm64.
        val ri = ins.indexOfFirst { isThumbRet(it.size, it.hw1, it.hw2) }.let { if (it < 0) ins.size else it }
        if ((0 until ri).none { isThumbMvnImm(ins[it].size, ins[it].hw1) }) return null
        // find/HasHeader bl: first bl after the getter fed by `add r0,rB,#HDR` (HDR >= MIN_HDR_OFF).
        var hdrOff = -1L
        var base = -1
        var findBl = -1
        var k = gi + 1
        while (k < minOf(gi + HDR_BL_SCAN, ins.size)) {
            if (isBl(ins[k])) {
                var j = k - 1
                while (j >= maxOf(gi, k - HDR_LOOKBACK)) {
                    val r = thumbAddR0RnImm(ins[j].size, ins[j].hw1, ins[j].hw2)
                    if (r != null && r.second >= MIN_HDR_OFF) {
                        base = r.first
                        hdrOff = r.second
                        findBl = k
                        break
                    }
                    j--
                }
                if (findBl >= 0) break
            }
            k++
        }
        if (findBl < 0) return null
        // guard: cbnz r0 within a few insns of the find bl.
        var guard = -1
        var g = findBl + 1
        while (g < minOf(findBl + 1 + GUARD_SCAN, ins.size)) {
            if (thumbCbnzR0Target(ins[g].size, ins[g].hw1, ins[g].addr) != null) {
                guard = g
                break
            }
            g++
        }
        if (guard < 0) return null
        // SetHeader bl after the guard, fed by `add r0,rB,#SET` (same rB, >= MIN_HDR_OFF, != HDR).
        var found = false
        var s = guard + 1
        while (s < minOf(guard + 1 + SET_SCAN, ins.size)) {
            if (isBl(ins[s])) {
                var j = s - 1
                while (j >= maxOf(guard, s - SET_LOOKBACK)) {
                    val r = thumbAddR0RnImm(ins[j].size, ins[j].hw1, ins[j].hw2)
                    if (r != null && r.first == base && r.second >= MIN_HDR_OFF && r.second != hdrOff) {
                        found = true
                        break
                    }
                    j--
                }
                if (found) break
            }
            s++
        }
        if (!found) return null
        val target = thumbCbnzR0Target(ins[guard].size, ins[guard].hw1, ins[guard].addr)!!
        return Chain(ins[guard].addr, target)
    }

    fun run(): LocateResult {
        if (elf.containsAnyString(MODE_FEATURES)) {
            return LocateResult.noMatch("settable-mode strings present -> not the inlined era (arm32)")
        }
        val getter = findGetter() ?: return LocateResult.noMatch("getter not found (arm32 inlined)")
        val quals = ArrayList<Chain>()
        for (callpc in blCallers(getter)) {
            val entry = entryOf(callpc) ?: continue
            findChain(entry, getter)?.let { quals.add(it) }
        }
        if (quals.size != 1) return LocateResult.noMatch("no unique inlined set-chain among getter callers (arm32)")
        val chain = quals[0]
        val patch = thumbForceBranch(chain.guardVa, chain.target) ?: return LocateResult.noMatch("guard branch out of range (arm32 inlined)")
        val foff = (t.foff + (chain.guardVa - taddr)).toInt()
        // The patch is the guard itself; the 16-byte verify window starts at the guard (verifyVa == adder).
        return LocateResult.emit(chain.guardVa, elf.sliceHex(foff, VERIFY_WINDOW), patch, chain.guardVa)
    }

    private companion object {
        /** The same settable-mode strings the mode era gates on; absent in the inlined era (the discriminator). */
        val MODE_FEATURES = listOf(
            "WebViewXRequestedWithHeaderControl",
            "WebViewXRequestedWithHeaderManifestAllowList",
            "RequestedWithHeader.CommittedHeaderMode",
        )

        /** Halfwords searched before an `add rD,pc` for its feeding literal load or movw/movt. */
        const val PIC_LOOKBACK = 10

        /** Instructions decoded from a getter-caller entry. */
        const val CHAIN_WINDOW = 120

        /** Instructions after the getter searched for the find bl. */
        const val HDR_BL_SCAN = 16

        /** Instructions before a bl searched for the `add r0,rB,#off` that feeds it. */
        const val HDR_LOOKBACK = 6

        /** Instructions after the find bl searched for the cbnz guard. */
        const val GUARD_SCAN = 4

        /** Instructions after the guard searched for the SetHeader bl. */
        const val SET_SCAN = 24

        /** Instructions before the SetHeader bl searched for its `add r0,rB,#SET`. */
        const val SET_LOOKBACK = 8
    }
}
