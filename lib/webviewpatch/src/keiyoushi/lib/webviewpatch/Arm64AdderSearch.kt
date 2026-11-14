package keiyoushi.lib.webviewpatch

// ---- shared gate thresholds (era locators read these when deciding emit/confidence) ----

/** retIdx below which the enclosing function is small enough to treat as a standalone adder. */
internal const val RET_IDX_CAP = 64

/** Confidence boost: an even tighter first-RET index. */
internal const val HIGH_CONF_RET_IDX = 58

/** A real adder body carries at most this many bl calls. */
internal const val MAX_BL = 16

/** req.headers / cors-exempt struct offsets sit at or above this. */
internal const val MIN_HDR_OFF = 0x100L

/** Committed-mode field offset floor. */
internal const val MIN_MODE_OFF = 0x10L

/** Plausible small-struct offset ceiling (confidence gate). */
internal const val MAX_STRUCT_OFF = 0x600L

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

    private fun wordAt(va: Long): Long = elf.u32le(tfoff + (va - taddr).toInt())

    /**
     * The X-Requested-With getter VA, found once per `.so` (a full `.text` scan). The three arm64 era
     * strategies share one [Arm64AdderSearch] per [Elf], so this and [getterCallers] are computed once
     * rather than up to three times per locate.
     */
    val getter: Long? by lazy { findGetter() }

    /** The getter's bl callers, enumerated once; empty when the getter is absent. */
    val getterCallers: List<Long> by lazy { getter?.let { blCallers(it) } ?: emptyList() }

    private val strRanges: List<LongArray> = elf.strSections().map { longArrayOf(it.addr, it.addr + it.size) }
    private fun inStr(va: Long): Boolean = strRanges.any { va >= it[0] && va < it[1] }

    // ADRP(+ADD/LDR) pointer xrefs resolving to any VA in targets -> {va: [adrp pc,...]}.
    private fun pointerXrefs(targets: Set<Long>): Map<Long, MutableList<Long>> {
        val hits = HashMap<Long, MutableList<Long>>()
        for (v in targets) hits[v] = ArrayList()
        for (i in 0 until n) {
            val w = word(i)
            if (!isAdrp(w)) continue
            val pc = taddr + 4L * i
            val page = adrpPage(pc, w)
            val reg = rd(w)
            var k = 1
            while (k <= 5) {
                val j = i + k
                if (j >= n) break
                val nw = word(j)
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
        }
        return hits
    }

    // Nearest function prologue at/before addr: paciasp or stp x29,x30,[sp,#-k]!.
    fun entryOf(addr: Long): Long? {
        val idx = ((addr - taddr) / 4).toInt()
        var i = idx
        val lo = maxOf(0, idx - ENTRY_SCAN_BACK)
        while (i >= lo) {
            val wi = word(i)
            if (isPaciasp(wi)) return taddr + 4L * i
            if (isStpFpLr(wi)) return taddr + 4L * i
            i--
        }
        return null
    }

    fun blCallers(target: Long): List<Long> {
        val out = ArrayList<Long>()
        for (i in 0 until n) {
            val w = word(i)
            if (!isBl(w)) continue
            val pc = taddr + 4L * i
            if (blTarget(pc, w) == target) out.add(pc)
        }
        return out
    }

    fun findGetter(): Long? {
        val rq = elf.findTails("Requested")
        val wi = elf.findTails("With")
        if (rq.isEmpty() || wi.isEmpty()) return null
        val hits = pointerXrefs((rq + wi).toHashSet())
        val byRq = HashMap<Long, Boolean>()
        val byWi = HashMap<Long, Boolean>()
        for (a in rq) for (pc in hits[a].orEmpty()) entryOf(pc)?.let { byRq[it] = true }
        for (a in wi) for (pc in hits[a].orEmpty()) entryOf(pc)?.let { byWi[it] = true }
        val cands = byRq.keys.filter { byWi.containsKey(it) }
        return if (cands.size == 1) cands[0] else null
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
        var confidence = "medium"
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
        // ret_idx: first RET
        var ri: Int? = null
        for (k in 0 until maxLen) {
            if (isRet(word(baseIdx + k))) {
                ri = k
                break
            }
        }
        f.retIdx = ri
        val bodyLen = if (ri != null) ri + 1 else maxLen
        // n_bl in body
        for (k in 0 until bodyLen) if (isBl(word(baseIdx + k))) f.nBl++
        // str_refs + err_imm (MOVN)
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
