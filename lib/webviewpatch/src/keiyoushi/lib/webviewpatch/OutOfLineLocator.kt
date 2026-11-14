package keiyoushi.lib.webviewpatch

/**
 * arm64 M150+ era: the X-Requested-With adder is a standalone pure function; the patch is RET-at-entry.
 *
 * Emits only when exactly one getter-caller is a PURE small adder (a first RET, no string refs, no
 * error immediates). That gate is the pivot that keeps this era disjoint from the inlined one, which
 * accepts only its negation.
 */
internal class OutOfLineLocator(elf: Elf) {
    private val search = elf.arm64Search()

    private fun emittable(f: Arm64AdderSearch.Feat): Boolean = f.retIdx != null && f.retIdx!! < RET_IDX_CAP && f.nBl <= MAX_BL && f.hasCond && f.tailBls >= 2 &&
        f.hdrOff != null && f.hdrOff!! >= MIN_HDR_OFF && f.strRefs == 0 && !f.errImm

    fun run(): LocateResult {
        if (search.getter == null) return LocateResult.noMatch("arm64", "getter not found")
        val callers = search.getterCallers
        val results = ArrayList<Arm64AdderSearch.Feat>()
        for (callpc in callers) {
            val entry = search.entryOf(callpc) ?: continue
            results.add(search.features(entry, callpc))
        }
        val quals = results.filter { emittable(it) }
        if (quals.size != 1) {
            return LocateResult.noMatch(
                "arm64",
                "no clean standalone adder among callers (inlined set -> RET unsafe -> fall back)",
            )
        }
        val f = quals[0]
        f.confidence = if (f.retIdx!! < HIGH_CONF_RET_IDX && f.tailBls >= 2 && f.hdrOff!! in MIN_HDR_OFF..MAX_STRUCT_OFF) {
            "high"
        } else {
            "medium"
        }
        val foff = (search.t.foff + (f.entry - search.taddr)).toInt()
        // RET-at-entry: the verify window starts at the adder entry, so verifyVa == adder.
        return LocateResult.emit("arm64", f.entry, f.confidence, search.elf.sliceHex(foff, 16), "c0035fd6", f.entry)
    }
}
