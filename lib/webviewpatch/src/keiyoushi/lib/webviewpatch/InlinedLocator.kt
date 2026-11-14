package keiyoushi.lib.webviewpatch

/**
 * arm64 M142/M146 era: the adder is inlined into the request-setup function (the settable-mode API is
 * also gone); the patch NOPs the inner `SetHeader` bl.
 *
 * Disjoint from [OutOfLineLocator] by the exact negation of its gate — the chain is accepted ONLY when
 * the enclosing function is NON-pure, so no single function can satisfy both eras.
 */
internal class InlinedLocator(elf: Elf) {
    private val search = elf.arm64Search()

    private data class Chain(val setVa: Long, val hdrOff: Long, val setOff: Long)

    /**
     * getter -> find(req.headers + off) -> conditional guard -> host_package_name() ->
     * SetHeader(cors_exempt map + off2). Returns the SetHeader bl VA (NOP target) and the two struct
     * offsets, or null. Structural, fail-closed.
     */
    private fun findSetChain(baseIdx: Int, giWord: Int, maxLen: Int): Chain? {
        // 1. find/HasHeader bl: first bl after the getter whose x0 = add xBase,#off (off >= MIN_HDR_OFF).
        var hdrBl = -1
        var hdrBase = -1
        var hdrOff = -1L
        var k = giWord + 1
        while (k < minOf(giWord + HDR_BL_SCAN, maxLen)) {
            if (isBl(search.word(baseIdx + k))) {
                var j = k - 1
                while (j > maxOf(giWord, k - HDR_LOOKBACK)) {
                    val r = addX0Imm(search.word(baseIdx + j))
                    if (r != null && r.second >= MIN_HDR_OFF) {
                        hdrBase = r.first
                        hdrOff = r.second
                        hdrBl = k
                        break
                    }
                    j--
                }
                if (hdrBl >= 0) break
            }
            k++
        }
        if (hdrBl < 0) return null

        // 2. conditional guard within a few insns of the find.
        var cond = -1
        var c = hdrBl + 1
        while (c < minOf(hdrBl + COND_SCAN, maxLen)) {
            if (isCondBranch(search.word(baseIdx + c))) {
                cond = c
                break
            }
            c++
        }
        if (cond < 0) return null

        // 3. host_package_name() bl: its result (x0) immediately consumed as a pointer (load [x0,...]).
        var pkgBl = -1
        var p = cond + 1
        while (p < minOf(cond + PKG_SCAN, maxLen)) {
            if (isBl(search.word(baseIdx + p)) && p + 1 < maxLen && isLoadBaseX0(search.word(baseIdx + p + 1))) {
                pkgBl = p
                break
            }
            p++
        }
        if (pkgBl < 0) return null

        // 4. SetHeader bl after host_package_name: preceded by add x0,hdrBase,#off2 (off2 >= MIN_HDR_OFF, the
        //    cors-exempt map, distinct from req.headers off). The first bl with no such add -> bail.
        var si = pkgBl + 1
        while (si < minOf(pkgBl + SET_HEADER_SCAN, maxLen)) {
            if (isBl(search.word(baseIdx + si))) {
                var j = si - 1
                while (j > maxOf(pkgBl, si - SET_HEADER_LOOKBACK)) {
                    val r = addX0Imm(search.word(baseIdx + j))
                    if (r != null && r.first == hdrBase && r.second >= MIN_HDR_OFF && r.second != hdrOff) {
                        return Chain(search.taddr + 4L * (baseIdx + si), hdrOff, r.second)
                    }
                    j--
                }
                return null
            }
            si++
        }
        return null
    }

    fun run(): LocateResult {
        if (search.getter == null) return LocateResult.noMatch("arm64", "getter not found")
        val quals = ArrayList<Chain>()
        for (callpc in search.getterCallers) {
            val entry = search.entryOf(callpc) ?: continue
            val f = search.features(entry, callpc)
            // Disjointness pivot: the out-of-line gate emits iff the enclosing function is a PURE small
            // adder; the inlined chain is accepted ONLY in its negation, so the two never collide.
            val pure = f.strRefs == 0 && !f.errImm && f.retIdx != null && f.retIdx!! < RET_IDX_CAP
            if (pure) continue
            val baseIdx = ((entry - search.taddr) / 4).toInt()
            val giWord = ((callpc - entry) / 4).toInt()
            findSetChain(baseIdx, giWord, minOf(INLINED_WINDOW, search.n - baseIdx))?.let { quals.add(it) }
        }
        if (quals.size != 1) {
            return LocateResult.noMatch(
                "arm64",
                "no unique inlined set-chain in a non-pure getter-caller (out-of-line era or no inlined adder -> fall back)",
            )
        }
        val chain = quals[0]
        val setFoff = (search.t.foff + (chain.setVa - search.taddr)).toInt()
        val conf = if (chain.hdrOff in MIN_HDR_OFF..MAX_STRUCT_OFF &&
            chain.setOff in MIN_HDR_OFF..MAX_STRUCT_OFF && chain.setOff != chain.hdrOff
        ) {
            "high"
        } else {
            "medium"
        }
        // NOP target: the verify window is the 16 bytes ending at and including the SetHeader bl, so it
        // starts 12 bytes before the patch offset.
        return LocateResult.emit("arm64", chain.setVa, conf, search.elf.sliceHex(setFoff - 12, 16), "1f2003d5", chain.setVa - 12)
    }

    private companion object {
        /** HasHeader bl search span after the getter call. */
        const val HDR_BL_SCAN = 16

        /** Look-back span for the `add x0,#off` feeding a bl. */
        const val HDR_LOOKBACK = 8

        /** Conditional-guard search span after the find. */
        const val COND_SCAN = 7

        /** host_package_name() bl search span after the guard. */
        const val PKG_SCAN = 20

        /** SetHeader bl search span after host_package_name. */
        const val SET_HEADER_SCAN = 14

        /** Look-back span for the `add x0,#off2` feeding the SetHeader bl. */
        const val SET_HEADER_LOOKBACK = 9

        /** Forward span (words) the set-chain is searched over. */
        const val INLINED_WINDOW = 300
    }
}
