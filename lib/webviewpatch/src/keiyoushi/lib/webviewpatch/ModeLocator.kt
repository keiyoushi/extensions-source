package keiyoushi.lib.webviewpatch

/**
 * arm64 M110-M130 era: the X-Requested-With handler is a non-pure function that commits a requested-with
 * mode and dispatches on it from mode-dependent SetHeader inject sites. The patch forces the committed-mode
 * read (`ldr wN,[base,#off]`) to `movz wN,#0`, so the dispatch always takes the no-inject (NO_HEADER) branch.
 *
 * Disjoint from [InlinedLocator] by requiring the settable-mode feature strings to be PRESENT (gone in
 * M142/M146), and from [OutOfLineLocator] because the mode function is non-pure.
 */
internal class ModeLocator(elf: Elf) {
    private val search = elf.arm64Search()

    private data class ModeChain(val modeLdrVa: Long, val modeReg: Int, val modeOff: Long, val hdrOff: Long)

    // The settable-mode feature strings. Their presence is the era discriminator: gone in the cors-exempt
    // inlined M142/M146 builds, so this gate alone keeps the mode and inlined strategies mutually exclusive.
    private val modeFeatures = listOf(
        "WebViewXRequestedWithHeaderControl",
        "WebViewXRequestedWithHeaderManifestAllowList",
        "RequestedWithHeader.CommittedHeaderMode",
    )

    /**
     * getter -> HasHeader(req+off) -> header-present guard (tbz w0) -> at the header-absent target a committed-
     * mode read `ldr wN,[base,#MODE_OFF]` whose MODE_OFF equals a `str w?,[base,#MODE_OFF]` emitted before the
     * getter (the commit==dispatch field identity) -> a cbz/cbnz/cmp dispatch on wN. Returns the read's VA and
     * its destination register (the one the `movz #0` must target), or null. Structural, fail-closed.
     */
    private fun findModeChain(baseIdx: Int, giWord: Int, maxLen: Int): ModeChain? {
        // 1. HasHeader bl: first bl after the getter preceded (<=lookback) by add x0,base,#off (off >= MIN_HDR_OFF).
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

        // 2. header-present guard: tbz/tbnz w0 within a few insns of the HasHeader call -> absent-branch target.
        var absentTgt = -1L
        var g = hdrBl + 1
        while (g < minOf(hdrBl + GUARD_SCAN, maxLen)) {
            val tgt = tbzW0Target(search.word(baseIdx + g), search.taddr + 4L * (baseIdx + g))
            if (tgt != null) {
                absentTgt = tgt
                break
            }
            g++
        }
        if (absentTgt < 0) return null

        // 3. the committed-mode read lives at the absent-branch target: ldr wN,[hdrBase,#off] (off >= MIN_MODE_OFF).
        val tci = ((absentTgt - search.taddr) / 4).toInt() - baseIdx
        if (tci < 0 || tci >= maxLen) return null
        var modeLdr = -1
        var modeReg = -1
        var modeOff = -1L
        var m = tci
        while (m < minOf(tci + READ_SCAN, maxLen)) {
            val r = ldrWImm(search.word(baseIdx + m))
            if (r != null && r.second == hdrBase && r.third >= MIN_MODE_OFF) {
                modeReg = r.first
                modeOff = r.third
                modeLdr = m
                break
            }
            m++
        }
        if (modeLdr < 0) return null

        // 4. commit==dispatch offset identity: a str w?,[hdrBase,#MODE_OFF] before the getter pins the field.
        var commit = false
        var c = 0
        while (c < giWord) {
            val r = strWImm(search.word(baseIdx + c))
            if (r != null && r.second == hdrBase && r.third == modeOff) {
                commit = true
                break
            }
            c++
        }
        if (!commit) return null

        // 5. the read feeds a multi-way mode dispatch (cbz/cbnz wN, or cmp wN,#imm) within a few insns.
        var disp = false
        var d = modeLdr + 1
        while (d < minOf(modeLdr + DISPATCH_SCAN, maxLen)) {
            if (dispatchesOn(search.word(baseIdx + d), modeReg)) {
                disp = true
                break
            }
            d++
        }
        if (!disp) return null

        return ModeChain(search.taddr + 4L * (baseIdx + modeLdr), modeReg, modeOff, hdrOff)
    }

    fun run(): LocateResult {
        // Era GATE: the settable-mode machinery must be present (vs the cors-exempt inlined M142/M146 builds).
        if (!search.elf.containsAnyString(modeFeatures)) {
            return LocateResult.noMatch("arm64", "settable-mode feature strings absent -> not the mode era (fall back)")
        }
        if (search.getter == null) return LocateResult.noMatch("arm64", "getter not found")
        val quals = ArrayList<Pair<ModeChain, Boolean>>() // chain + pure flag (for confidence)
        for (callpc in search.getterCallers) {
            val entry = search.entryOf(callpc) ?: continue
            val f = search.features(entry, callpc)
            // The enclosing mode function is non-pure (UMA string refs / error imms); the pure flag feeds
            // confidence, matching the out-of-line gate's negation.
            val pure = f.strRefs == 0 && !f.errImm && f.retIdx != null && f.retIdx!! < RET_IDX_CAP
            val baseIdx = ((entry - search.taddr) / 4).toInt()
            val giWord = ((callpc - entry) / 4).toInt()
            val maxLen = minOf(MODE_WINDOW, search.n - baseIdx)
            findModeChain(baseIdx, giWord, maxLen)?.let { quals.add(it to pure) }
        }
        if (quals.size != 1) {
            return LocateResult.noMatch(
                "arm64",
                "no unique committed-mode dispatch chain among getter callers (not the mode-era adder -> fall back)",
            )
        }
        val (chain, pure) = quals[0]
        val conf = if (chain.modeOff in MIN_MODE_OFF..MAX_STRUCT_OFF && !pure && chain.hdrOff in MIN_HDR_OFF..MAX_STRUCT_OFF) {
            "high"
        } else {
            "medium"
        }
        val foff = (search.t.foff + (chain.modeLdrVa - search.taddr)).toInt()
        // Patch the committed-mode read in place with movz wN,#0 (forces the NO_HEADER dispatch branch). The
        // patch site is NOT the function entry; the 16-byte verify window is centred on the read (starts 8
        // bytes before it), so verifyVa == patch site - 8.
        return LocateResult.emit(
            "arm64",
            chain.modeLdrVa,
            conf,
            search.elf.sliceHex(foff - 8, 16),
            movzWnZero(chain.modeReg),
            chain.modeLdrVa - 8,
        )
    }

    private companion object {
        /** HasHeader bl search span after the getter call. */
        const val HDR_BL_SCAN = 16

        /** Look-back span for the `add x0,#off` feeding the HasHeader bl. */
        const val HDR_LOOKBACK = 9

        /** header-present tbz guard search span after HasHeader. */
        const val GUARD_SCAN = 6

        /** committed-mode ldr search span at the absent-branch target. */
        const val READ_SCAN = 6

        /** dispatch search span after the mode read. */
        const val DISPATCH_SCAN = 5

        /** Forward span (words) the mode-chain is searched over. */
        const val MODE_WINDOW = 600
    }
}
