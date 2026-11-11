package keiyoushi.lib.webviewpatch

// ---- arm32 gate thresholds and scan windows (run()/features() read these when deciding emit/confidence) ----

/** entryOf walks back at most this many halfwords looking for the function prologue. */
internal const val ENTRY_SCAN_BACK = 4000

/** run() decodes at most this many instructions forward from a candidate entry. */
internal const val MAX_DECODE_INSNS = 200

/** resolvedVaAt decodes a window this many instructions wide to recover a PIC base. */
internal const val PIC_RESOLVE_WINDOW = 64

/** ...and starts that window at most this many halfwords before the add-pc site. */
internal const val PIC_RESOLVE_LOOKBACK = 10

/** HasHeader: scan this many instructions past the getter bl for the first header-set bl. */
internal const val HDR_BL_SCAN = 30

/** ...looking back at most this many instructions from that bl for an `add r0,#large`. */
internal const val HDR_ADD_LOOKBACK = 7

/** The committed guard must appear within this many instructions of the header bl. */
internal const val COND_SCAN = 8

/** Count tail bl calls within this many instructions of the header bl. */
internal const val TAIL_BL_SCAN = 24

/** A standalone adder makes at least this many tail bl calls (set-header fan-out). */
internal const val MIN_TAIL_BLS = 2

/** First-RET index below which the enclosing function is small enough to be a standalone adder. */
internal const val RET_IDX_CAP_ARM32 = 90

/** An `add r0,#imm` header offset counts only above this (a disassembler renders it hex at > 9). */
internal const val ADD_IMM_MIN = 9

/** The 16-byte verify window the emit records at the adder entry. */
internal const val VERIFY_WINDOW = 16

/**
 * arm32 / Thumb-2 branch of the locator.
 *
 * Linearly decodes [Thumb] instructions from a candidate function entry — the 16/32 width rule
 * reproduces the correct instruction boundaries — and summarises each getter caller's body into
 * [Feat], the same structural shape the arm64 search uses. State is assumed Thumb (fail-closed on an
 * implied ARM-mode function); a clean standalone adder is patched to bx lr at its entry.
 */
internal class ThumbLocator(val elf: Elf) {
    private val t = elf.text()
    private val taddr = t.addr
    private val tfoff = t.foff.toInt()
    private val nhw = (t.size / 2).toInt()
    private val strRanges: List<LongArray> = elf.strSections().map { longArrayOf(it.addr, it.addr + it.size) }
    private fun inStr(va: Long): Boolean = strRanges.any { va >= it[0] && va < it[1] }

    private fun hwIdx(i: Int): Int = elf.hw(tfoff + i * 2) // halfword at .text index i

    // ---- one decoded Thumb instruction ----
    inner class TInsn(val addr: Long, val size: Int, val hw1: Int, val hw2: Int) {
        val isBl: Boolean get() = isThumbBl(size, hw1, hw2)

        fun retKind(): String? = thumbRetKind(size, hw1, hw2)

        val isMvnImm: Boolean get() = isThumbMvnImm(size, hw1)

        /** ADD(imm) with Rd==0, base not sp/pc, and a real (rendered-hex > 9) immediate -> imm, else null. */
        fun addR0Imm(): Long? = thumbAddR0Imm(size, hw1, hw2)?.takeIf { it > ADD_IMM_MIN }

        val isCond: Boolean get() = isThumbCond(size, hw1)
    }

    private fun decodeOne(idx: Int): TInsn {
        val hw1 = hwIdx(idx)
        return if (isThumb32(hw1) && idx + 1 < nhw) {
            TInsn(taddr + 2L * idx, 4, hw1, hwIdx(idx + 1))
        } else {
            TInsn(taddr + 2L * idx, 2, hw1, 0)
        }
    }

    // Linear decode from start index until an instruction's address would reach endVa (exclusive),
    // or maxInsns reached.
    private fun decodeFrom(startIdx: Int, endVaExclusive: Long, maxInsns: Int): List<TInsn> {
        val out = ArrayList<TInsn>()
        var i = startIdx
        while (i < nhw && out.size < maxInsns) {
            val addr = taddr + 2L * i
            if (endVaExclusive in 1..addr) break
            val ins = decodeOne(i)
            out.add(ins)
            i += ins.size / 2
        }
        return out
    }

    // ---- MOVW/MOVT/LDR-literal feeders (resolver) ----
    private fun movwImm(ins: TInsn): Int? = thumbMovwImm16(ins.size, ins.hw1, ins.hw2)
    private fun movtImm(ins: TInsn): Int? = thumbMovtImm16(ins.size, ins.hw1, ins.hw2)

    // LDR literal: returns (rd, loadedValue) or null
    private fun ldrLiteral(ins: TInsn): Pair<Int, Long>? {
        val (rt, a) = thumbLdrLiteralAddr(ins.size, ins.hw1, ins.hw2, ins.addr) ?: return null
        return elf.rdword(a)?.let { rt to it }
    }

    // Resolve the PIC va formed by an `add rD,pc` site at halfword index ai, or null.
    private fun resolvedVaAt(ai: Int): Long? {
        val h = hwIdx(ai)
        if (!isThumbAddPc(h)) return null
        val aAddr = taddr + 2L * ai
        val pcbase = aAddr + 4
        val rdn = thumbAddPcRd(h)
        val window = decodeFrom(maxOf(0, ai - PIC_RESOLVE_LOOKBACK), aAddr, PIC_RESOLVE_WINDOW)
        var movw: Int? = null
        var movt: Int? = null
        var lit: Long? = null
        for (x in window) {
            if (x.addr >= aAddr) break
            movwImm(x)?.let { if (thumbMovwtRd(x.hw2) == rdn) movw = it }
            movtImm(x)?.let { if (thumbMovwtRd(x.hw2) == rdn) movt = it }
            ldrLiteral(x)?.let { (rt, v) -> if (rt == rdn) lit = v }
        }
        val delta: Long = when {
            movw != null && movt != null -> ((movt!!.toLong() shl 16) or movw!!.toLong())
            lit != null -> lit!!
            else -> return null
        }
        return (delta + pcbase) and 0xFFFFFFFFL
    }

    private fun resolveStringRefs(targets: Set<Long>): Map<Long, Long> {
        val hits = HashMap<Long, Long>()
        for (ai in 0 until nhw) {
            val va = resolvedVaAt(ai) ?: continue
            if (va in targets) hits[taddr + 2L * ai] = va
        }
        return hits
    }

    // ---- function boundary ----
    private fun entryOf(addr: Long): Long? {
        var i = ((addr - taddr) / 2).toInt()
        val lo = maxOf(0, i - ENTRY_SCAN_BACK)
        while (i >= lo) {
            val h = hwIdx(i)
            if (isThumbPushLrT1(h)) return taddr + 2L * i // push {..,lr}
            if (h == PUSH_LR_W_HW1 && i + 1 < nhw && (hwIdx(i + 1) and PUSH_LR_W_LR_BIT) != 0) return taddr + 2L * i // push.w {..,lr}
            i--
        }
        return null
    }

    // ---- Thumb-2 BL/BLX xref enumeration ----
    private fun blCallers(target: Long): List<Long> {
        val out = ArrayList<Long>()
        val tgt = target and 0xFFFFFFFEL
        for (i in 0 until nhw - 1) {
            val pc = taddr + 2L * i
            val dst = thumbBlTarget(hwIdx(i), hwIdx(i + 1), pc) ?: continue
            if (dst == tgt) out.add(pc)
        }
        return out
    }

    private fun findGetter(): Long? {
        val rq = elf.findTails("Requested")
        val wi = elf.findTails("With")
        if (rq.isEmpty() || wi.isEmpty()) return null
        val rqSet = rq.toHashSet()
        val wiSet = wi.toHashSet()
        val hits = resolveStringRefs((rq + wi).toHashSet())
        val byRq = HashMap<Long, Boolean>()
        val byWi = HashMap<Long, Boolean>()
        for ((pc, va) in hits) {
            val e = entryOf(pc) ?: continue
            if (va in rqSet) byRq[e] = true
            if (va in wiSet) byWi[e] = true
        }
        val cands = byRq.keys.filter { byWi.containsKey(it) }
        return if (cands.size == 1) cands[0] else null
    }

    private class Feat {
        var hdrOff: Long? = null
        var hasCond = false
        var tailBls = 0
        var retIdx: Int? = null
        var retKind: String? = null
        var nBl = 0
        var strRefs = 0
        var errImm = false
        var entry = 0L
    }

    private fun features(insns: List<TInsn>, gi: Int): Feat {
        val f = Feat()
        var ri: Int? = null
        var rk: String? = null
        for (k in insns.indices) {
            val kk = insns[k].retKind()
            if (kk != null) {
                ri = k
                rk = kk
                break
            }
        }
        f.retIdx = ri
        f.retKind = rk
        val bodyEnd = if (ri != null) ri + 1 else insns.size
        for (k in 0 until bodyEnd) if (insns[k].isBl) f.nBl++
        for (k in 0 until bodyEnd) if (insns[k].isMvnImm) f.errImm = true
        // Count add-pc string refs in the body and re-impose str_refs==0 like arm64, so a body that
        // loads string literals (an inlined setter) is rejected rather than returned-at-entry.
        for (k in 0 until bodyEnd) {
            val ins = insns[k]
            if (ins.size == 2 && isThumbAddPc(ins.hw1)) {
                val va = resolvedVaAt(((ins.addr - taddr) / 2).toInt())
                if (va != null && inStr(va)) f.strRefs++
            }
        }
        // HasHeader: first bl after getter; look back <=6 for add r0,#large
        var hdrBl: Int? = null
        var k = gi + 1
        while (k < minOf(gi + HDR_BL_SCAN, insns.size)) {
            if (insns[k].isBl) {
                var j = k - 1
                val lo = maxOf(gi, k - HDR_ADD_LOOKBACK)
                while (j > lo) {
                    val imm = insns[j].addR0Imm()
                    if (imm != null) {
                        f.hdrOff = imm
                        hdrBl = k
                        break
                    }
                    j--
                }
                if (hdrBl != null) break
            }
            k++
        }
        val base = hdrBl ?: (gi + 1)
        var c = base
        while (c < minOf(base + COND_SCAN, insns.size)) {
            if (insns[c].isCond) {
                f.hasCond = true
                break
            }
            c++
        }
        var b = base
        while (b < minOf(base + TAIL_BL_SCAN, insns.size)) {
            if (insns[b].isBl) f.tailBls++
            b++
        }
        return f
    }

    private fun emittable(f: Feat): Boolean = f.retIdx != null && f.retIdx!! < RET_IDX_CAP_ARM32 && f.nBl <= MAX_BL && f.hasCond &&
        f.tailBls >= MIN_TAIL_BLS && f.hdrOff != null && f.hdrOff!! >= MIN_HDR_OFF && f.strRefs == 0 && !f.errImm

    fun run(): LocateResult {
        val getter = findGetter() ?: return LocateResult.noMatch("arm32", "getter not found (arm32)")
        val callers = blCallers(getter)
        val results = ArrayList<Feat>()
        for (callpc in callers) {
            val entry = entryOf(callpc) ?: continue
            val insns = decodeFrom(((entry - taddr) / 2).toInt(), -1, MAX_DECODE_INSNS)
            val gi = insns.indexOfFirst { it.addr == callpc }
            if (gi < 0) continue
            val f = features(insns, gi)
            f.entry = entry
            results.add(f)
        }
        val quals = results.filter { emittable(it) }
        if (quals.size != 1) {
            return LocateResult.noMatch(
                "arm32",
                "no clean standalone adder among getter callers (inlined/absent -> early-return unsafe -> fall back)",
            )
        }
        val f = quals[0]
        val foff = (t.foff + (f.entry - taddr)).toInt()
        // bx-lr at entry: the verify window starts at the adder entry, so verifyVa == adder.
        return LocateResult.emit("arm32", f.entry, "medium", elf.sliceHex(foff, VERIFY_WINDOW), THUMB_BX_LR_PATCH, f.entry)
    }
}
