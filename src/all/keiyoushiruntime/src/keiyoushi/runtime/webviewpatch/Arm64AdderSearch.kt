package keiyoushi.runtime.webviewpatch

// ---- shared gate thresholds (era locators read these when deciding whether to emit) ----

/** retIdx below which the enclosing function is small enough to treat as a standalone adder. */
internal const val RET_IDX_CAP = 64

/** A real adder body carries at most this many bl calls. */
internal const val MAX_BL = 16

/** req.headers / cors-exempt struct offsets sit at or above this. */
internal const val MIN_HDR_OFF = 0x100L

/** Committed-mode field offset floor. */
internal const val MIN_MODE_OFF = 0x10L

/**
 * Shared arm64 search primitives over a WebView `.so`'s `.text`.
 *
 * Finds the X-Requested-With getter, enumerates its bl callers, walks function boundaries, and
 * summarises each caller's body into [Feat]. The three arm64 era locators (out-of-line, inlined,
 * mode) each own their era-specific chain logic but share this structural layer.
 */
internal class Arm64AdderSearch(val elf: Elf) {
    val t = elf.text()
    val taddr = t.addr
    private val tfoff = t.foff.toInt()
    val n = (t.size / 4).toInt()

    fun word(i: Int): Long = elf.u32le(tfoff + i * 4)

    // Reused chunk buffer for the single fused `.text` sweep ([runSweep]). It runs at most once per
    // [Elf] on one thread, so a single shared buffer is safe. Bulk-filling it and walking the heap
    // array is ~3x faster cold than a bounds-checked get() per word.
    private val scanBuf = IntArray(SCAN_CHUNK_WORDS)

    private class SweepResult(val getter: Long?, val callers: List<Long>)

    /** The one fused `.text` sweep, run once: it yields both the getter VA and its bl callers. */
    private val sweepResult: SweepResult by lazy { runSweep() }

    /**
     * The X-Requested-With getter VA, found once per `.so` by the single [runSweep]. The three arm64
     * era strategies share one [Arm64AdderSearch] per [Elf], so this and [getterCallers] are computed
     * once rather than up to three times per locate.
     */
    val getter: Long? get() = sweepResult.getter

    /** The getter's bl callers (ascending by pc), enumerated by the same sweep; empty when the getter is absent. */
    val getterCallers: List<Long> get() = sweepResult.callers

    private val strRanges: List<LongArray> = elf.strSections().map { longArrayOf(it.addr, it.addr + it.size) }
    private fun inStr(va: Long): Boolean = strRanges.any { va >= it[0] && va < it[1] }

    /**
     * One pass over `.text` that does the work the getter-search and the caller-search used to split into
     * two full sweeps. On the pass it both (a) records ADRP(+ADD/LDR) pointer xrefs to the Requested/With
     * string VAs (to identify the getter) and (b) records every BL site (its target VA and word index).
     * After the getter is known, the recorded BLs are filtered in memory for `target == getter` — no
     * second sweep. The BL buffers are held in fixed 64K-entry segments (no doubling-and-copy) and go out
     * of scope when this returns, so their ~17.6 MB peak does not linger into the rest of startup.
     *
     * Byte-identical to the old two-sweep result: the ADRP xref/getter logic is unchanged, and the BL
     * filter visits sites in the same ascending-pc order the old `blCallers` scan did.
     */
    private fun runSweep(): SweepResult {
        val rq = elf.findTails("Requested")
        val wi = elf.findTails("With")
        if (rq.isEmpty() || wi.isEmpty()) return SweepResult(null, emptyList())

        val targets = (rq + wi).toHashSet()
        val hits = HashMap<Long, MutableList<Long>>()
        for (v in targets) hits[v] = ArrayList()

        // BL sites in ascending-pc order, in fixed-size segments: the target as a signed word-offset
        // `(target - taddr)/4` (Int) + the call-site word index (Int). The offset is a bijection with the
        // target VA for this `.text` (both are 4-aligned; a BL's ±128 MB reach keeps it well inside Int),
        // so matching offsets is identical to matching VAs, at half the per-entry memory of a Long VA.
        val blTargetSegs = ArrayList<IntArray>()
        val blIdxSegs = ArrayList<IntArray>()
        var curT = IntArray(BL_SEGMENT)
        var curI = IntArray(BL_SEGMENT)
        blTargetSegs.add(curT)
        blIdxSegs.add(curI)
        var blN = 0
        var fill = 0

        val buf = scanBuf
        var i = 0
        while (i < n) {
            val len = minOf(buf.size, n - i)
            elf.words(tfoff + i * 4, buf, len)
            for (c in 0 until len) {
                val w = buf[c].toLong() and 0xFFFFFFFFL
                if (isAdrp(w)) {
                    val idx = i + c
                    val pc = taddr + 4L * idx
                    val page = adrpPage(pc, w)
                    val reg = rd(w)
                    var k = 1
                    while (k <= 5) {
                        val j = idx + k
                        if (j >= n) break
                        // The ADRP lookahead stays within the current chunk; read from the buffer when
                        // it does, falling to a single word() only across the chunk tail.
                        val nw = if (c + k < len) buf[c + k].toLong() and 0xFFFFFFFFL else word(j)
                        if (isAddImm(nw) && rn(nw) == reg) {
                            val va = page + addImmShifted(nw)
                            if (va in targets) hits[va]!!.add(pc)
                            break
                        }
                        if (isLdrXUimm(nw) && rn(nw) == reg) {
                            val va = page + ldrXUimmOff(nw)
                            if (va in targets) hits[va]!!.add(pc)
                            break
                        }
                        if (rd(nw) == reg) break
                        k++
                    }
                } else if (isBl(w)) {
                    if (fill == BL_SEGMENT) {
                        curT = IntArray(BL_SEGMENT)
                        curI = IntArray(BL_SEGMENT)
                        blTargetSegs.add(curT)
                        blIdxSegs.add(curI)
                        fill = 0
                    }
                    val idx = i + c
                    curT[fill] = ((blTarget(taddr + 4L * idx, w) - taddr) / 4).toInt()
                    curI[fill] = idx
                    fill++
                    blN++
                }
            }
            i += len
        }

        // getter: the unique function referencing both the Requested and With strings (the old findGetter).
        val byRq = HashMap<Long, Boolean>()
        val byWi = HashMap<Long, Boolean>()
        for (a in rq) for (pc in hits[a].orEmpty()) entryOf(pc)?.let { byRq[it] = true }
        for (a in wi) for (pc in hits[a].orEmpty()) entryOf(pc)?.let { byWi[it] = true }
        val cands = byRq.keys.filter { byWi.containsKey(it) }
        val getter = if (cands.size == 1) cands[0] else null

        // Filter the recorded BLs for target == getter, ascending-pc (the old second sweep's order).
        // Compare in the same word-offset space the sites were stored in; equivalent to target VA == getter.
        val callers = if (getter == null) {
            emptyList()
        } else {
            val getterOff = ((getter - taddr) / 4).toInt()
            val out = ArrayList<Long>()
            var remaining = blN
            var seg = 0
            while (remaining > 0) {
                val tSeg = blTargetSegs[seg]
                val iSeg = blIdxSegs[seg]
                val cnt = minOf(BL_SEGMENT, remaining)
                for (e in 0 until cnt) if (tSeg[e] == getterOff) out.add(taddr + 4L * iSeg[e])
                remaining -= cnt
                seg++
            }
            out
        }
        return SweepResult(getter, callers)
    }

    /**
     * The entry of the function containing [addr], or null. A pointer-auth function opens with
     * `paciasp` and saves its frame one or two instructions later, so the window is searched for
     * `paciasp` first: the `stp x29,x30,[sp,#-k]!` is the entry only of a function built without PAC.
     */
    fun entryOf(addr: Long): Long? {
        val idx = ((addr - taddr) / 4).toInt()
        val lo = maxOf(0, idx - ENTRY_SCAN_BACK)
        for (i in idx downTo lo) {
            if (isPaciasp(word(i))) return taddr + 4L * i
        }
        for (i in idx downTo lo) {
            if (isStpFpLr(word(i))) return taddr + 4L * i
        }
        return null
    }

    /** Structural summary of a getter-caller's body; the era gates read these fields. */
    class Feat {
        var hdrOff: Long? = null
        var hasCond = false
        var tailBls = 0
        var retIdx: Int? = null
        var nBl = 0
        var strRefs = 0
        var errImm = false
        var entry = 0L
    }

    private fun resolvesToStr(baseIdx: Int, k: Int, bodyLen: Int): Boolean {
        val w = word(baseIdx + k)
        if (!isAdrp(w)) return false
        val pc = taddr + 4L * (baseIdx + k)
        val page = adrpPage(pc, w)
        val reg = rd(w)
        var j = k + 1
        while (j < minOf(k + 4, bodyLen)) {
            val nx = word(baseIdx + j)
            // ADD(imm) with dest==rd
            if (isAddImmAny(nx) && rd(nx) == reg) return inStr(page + addImmShifted(nx))
            // LDR(imm,unsigned) with dest==rd -> disp = imm12*scale (scale by size)
            if (isLdstUimm(nx) && rd(nx) == reg) return inStr(page + ldstUimmDisp(nx))
            j++
        }
        return false
    }

    fun features(entry: Long, callpc: Long): Feat {
        val f = Feat()
        f.entry = entry
        val baseIdx = ((entry - taddr) / 4).toInt()
        val maxLen = minOf(FEATURES_WINDOW, n - baseIdx)
        val gi = ((callpc - entry) / 4).toInt()
        // retIdx: the first RET in the window
        var ri: Int? = null
        for (k in 0 until maxLen) {
            if (isRet(word(baseIdx + k))) {
                ri = k
                break
            }
        }
        f.retIdx = ri
        val bodyLen = if (ri != null) ri + 1 else maxLen
        // nBl: every bl inside the body
        for (k in 0 until bodyLen) if (isBl(word(baseIdx + k))) f.nBl++
        // strRefs + errImm (MOVN)
        for (k in 0 until bodyLen) {
            if (isAdrp(word(baseIdx + k)) && resolvesToStr(baseIdx, k, bodyLen)) f.strRefs++
            val w = word(baseIdx + k)
            if (isMovn(w)) f.errImm = true // MOVN (rendered as mov #-N)
        }
        // HasHeader: first BL after getter whose arg x0 = add x0,xN(!=sp),#imm (imm>0)
        var hdrBl: Int? = null
        var k = gi + 1
        while (k < minOf(gi + HDR_BL_SCAN, maxLen)) {
            if (isBl(word(baseIdx + k))) {
                var j = k - 1
                val lo = maxOf(gi, k - HDR_LOOKBACK)
                while (j > lo) {
                    val m = word(baseIdx + j)
                    val imm = addImmIfDest64(m, 0L)
                    if (imm != null && imm > 0 && rn(m) != 31L) {
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
        // conditional guard within COND_SCAN insns of HasHeader
        var c = base
        while (c < minOf(base + COND_SCAN, maxLen)) {
            if (isCondBranch(word(baseIdx + c))) {
                f.hasCond = true
                break
            }
            c++
        }
        // tail bls in guarded block
        var b = base
        while (b < minOf(base + TAIL_SCAN, maxLen)) {
            if (isBl(word(baseIdx + b))) f.tailBls++
            b++
        }
        return f
    }

    private companion object {
        /** Words per bulk-read chunk for the fused `.text` sweep (64K words = 256 KB heap, reused). */
        const val SCAN_CHUNK_WORDS = 1 shl 16

        /**
         * Entries per BL-record segment (64K). A segment is two IntArrays (target word-offsets + call-site
         * word indices, 256 KB each); the sweep appends full segments instead of doubling a single array, so
         * its ~17.6 MB peak for a ~100 MB `.text` never spikes from a resize-and-copy.
         */
        const val BL_SEGMENT = 1 shl 16

        /** Back-scan span (words) for the nearest prologue. */
        const val ENTRY_SCAN_BACK = 4000

        /** Forward span (words) features() summarises from an entry. */
        const val FEATURES_WINDOW = 200

        /** HasHeader bl search span after the getter call. */
        const val HDR_BL_SCAN = 30

        /** Look-back span for the `add x0,#off` feeding a HasHeader bl. */
        const val HDR_LOOKBACK = 7

        /** Conditional-guard search span after HasHeader. */
        const val COND_SCAN = 8

        /** Tail-bl counting span in the guarded block. */
        const val TAIL_SCAN = 24
    }
}
