package keiyoushi.lib.webviewpatch

/*
 * Shared arm64 (AArch64) machine-code decode layer for the XRW locator.
 *
 * arm64 is fixed-width (4 bytes), so a word is one instruction. These are the narrow forms the locate
 * needs — ADRP, ADD(imm), LDR(imm,unsigned), B/BL, RET, PACIASP, STP prologue, CBZ/CBNZ/TBZ/TBNZ,
 * B.cond, MOVN, MOVZ, LDR/STR(w), CMP/SUBS, LDP — as named encodings plus bit-exact predicates and
 * extractors (sign-extension and scale handling preserved). The three arm64 era locators share them.
 */

// ---- instruction encodings (value) and their decode masks ----

/** ADRP (PC-relative page address). */
internal const val ADRP = 0x90000000L
internal const val ADRP_MASK = 0x9F000000L

/** ADD (immediate), 64-bit (sf=1). */
internal const val ADD_IMM = 0x91000000L
internal const val ADD_IMM_MASK = 0xFF800000L

/** ADD (immediate), size-agnostic (sf bit masked out). */
internal const val ADD_IMM_ANY = 0x11000000L
internal const val ADD_IMM_ANY_MASK = 0x7F800000L

/** LDR (immediate, unsigned offset), 64-bit. */
internal const val LDR_X_UIMM = 0xF9400000L
internal const val LDR_X_UIMM_MASK = 0xFFC00000L

/** LDR/STR (immediate, unsigned offset), size-agnostic load/store family. */
internal const val LDST_UIMM = 0x39000000L
internal const val LDST_UIMM_MASK = 0x3B000000L

/** BL (branch with link). */
internal const val BL = 0x94000000L
internal const val BL_MASK = 0xFC000000L

/** RET. */
internal const val RET = 0xD65F0000L
internal const val RET_MASK = 0xFFFFFC1FL

/** PACIASP (pointer-auth prologue tag). */
internal const val PACIASP = 0xD503233FL

/** STP x29,x30,[sp,#-k]! prologue (Rt/Rt2 checked separately). */
internal const val STP_FP_LR = 0xA9800000L
internal const val STP_FP_LR_MASK = 0xFFC003E0L

/** Compare-and-branch / test-and-branch family (top byte). */
internal const val CBZ = 0x34000000L
internal const val CBNZ = 0x35000000L
internal const val TBZ = 0x36000000L
internal const val TBNZ = 0x37000000L
internal const val COMPARE_BRANCH_MASK = 0x7F000000L

/** B.cond (conditional branch immediate). */
internal const val B_COND = 0x54000000L
internal const val B_COND_MASK = 0xFF000010L

/** MOVN (rendered as mov #-N), size-agnostic. */
internal const val MOVN = 0x12800000L
internal const val MOVN_MASK = 0x7F800000L

/** MOVZ, 32-bit (the mode-era patch instruction, register in the low 5 bits). */
internal const val MOVZ_W = 0x52800000L

/** LDR/STR (immediate, unsigned offset), 32-bit (w register). */
internal const val LDR_W_UIMM = 0xB9400000L
internal const val STR_W_UIMM = 0xB9000000L
internal const val MEM_W_UIMM_MASK = 0xFFC00000L

/** SUBS (immediate); `cmp wN,#imm` is `subs wzr,wN,#imm`. */
internal const val SUBS_IMM = 0x71000000L
internal const val SUBS_IMM_MASK = 0x7F800000L

/** LDP/STP (load/store pair); load has the L bit set. */
internal const val LDST_PAIR = 0x28000000L
internal const val LDST_PAIR_MASK = 0x3E000000L
internal const val LDST_PAIR_LOAD_BIT = 0x400000L

// ---- register field extractors ----

/** Rd / Rt (destination), bits 0..4. */
internal fun rd(w: Long): Long = w and 0x1F

/** Rn (base), bits 5..9. */
internal fun rn(w: Long): Long = (w ushr 5) and 0x1F

// ---- predicates and extractors ----

internal fun isAdrp(w: Long): Boolean = (w and ADRP_MASK) == ADRP

/** Page VA an ADRP at [pc] forms (sign-extended 21-bit page immediate). */
internal fun adrpPage(pc: Long, w: Long): Long {
    val immlo = (w ushr 29) and 3
    val immhi = (w ushr 5) and 0x7FFFF
    var imm = (immhi shl 2) or immlo
    if (imm and (1L shl 20) != 0L) imm -= (1L shl 21)
    return (pc and 0xFFFL.inv()) + (imm shl 12)
}

internal fun isAddImm(w: Long): Boolean = (w and ADD_IMM_MASK) == ADD_IMM

internal fun isAddImmAny(w: Long): Boolean = (w and ADD_IMM_ANY_MASK) == ADD_IMM_ANY

/** The byte displacement an ADD(imm) applies (shift-12 handling); valid for the 64-bit and any-size forms. */
internal fun addImmShifted(w: Long): Long {
    val shift = (w ushr 22) and 3
    return ((w ushr 10) and 0xFFF) shl (if (shift == 1L) 12 else 0)
}

/** ADD(imm) 64-bit with dest == [reg] -> its displacement, else null. */
internal fun addImmIfDest64(w: Long, reg: Long): Long? {
    if (!isAddImm(w)) return null
    if (rd(w) != reg) return null
    return addImmShifted(w)
}

/** `add x0, xBase, #imm` (64-bit, base != sp) -> (base reg, imm); else null. */
internal fun addX0Imm(w: Long): Pair<Int, Long>? {
    if (!isAddImm(w)) return null // ADD(imm), sf=1
    if (rd(w) != 0L) return null // Rd == x0
    val base = rn(w).toInt()
    if (base == 31) return null // not sp
    return base to addImmShifted(w)
}

internal fun isLdrXUimm(w: Long): Boolean = (w and LDR_X_UIMM_MASK) == LDR_X_UIMM

/** Byte displacement of a 64-bit LDR(imm,unsigned). */
internal fun ldrXUimmOff(w: Long): Long = ((w ushr 10) and 0xFFF) * 8

internal fun isLdstUimm(w: Long): Boolean = (w and LDST_UIMM_MASK) == LDST_UIMM

/** Byte displacement of a size-agnostic LDR/STR(imm,unsigned) (scaled by the size field). */
internal fun ldstUimmDisp(w: Long): Long {
    val size = (w ushr 30) and 3
    return ((w ushr 10) and 0xFFF) shl size.toInt()
}

internal fun isBl(w: Long): Boolean = (w and BL_MASK) == BL

/** Absolute target of a BL at [pc] (sign-extended 26-bit immediate). */
internal fun blTarget(pc: Long, w: Long): Long {
    var imm = w and 0x03FFFFFF
    if (imm and (1L shl 25) != 0L) imm -= (1L shl 26)
    return pc + imm * 4
}

internal fun isRet(w: Long): Boolean = (w and RET_MASK) == RET

internal fun isPaciasp(w: Long): Boolean = w == PACIASP

/** `stp x29,x30,[sp,#-k]!` function prologue. */
internal fun isStpFpLr(w: Long): Boolean = (w and STP_FP_LR_MASK) == STP_FP_LR && rd(w) == 29L && ((w ushr 10) and 0x1F) == 30L

internal fun isMovn(w: Long): Boolean = (w and MOVN_MASK) == MOVN

/** cbz/cbnz/tbz/tbnz, or b.eq/b.ne — the guard set the out-of-line features() recognises. */
internal fun isCondBranch(w: Long): Boolean {
    val top = w and COMPARE_BRANCH_MASK
    if (top == CBZ || top == CBNZ || top == TBZ || top == TBNZ) return true
    return (w and B_COND_MASK) == B_COND && (w and 0xF) <= 1L // b.eq/b.ne
}

/** `tbz/tbnz w0, #bit, label` -> absolute target VA; else null. Requires a w register (bit31==0) and Rt==w0. */
internal fun tbzW0Target(w: Long, pc: Long): Long? {
    val top = w and COMPARE_BRANCH_MASK
    if (top != TBZ && top != TBNZ) return null
    if ((w ushr 31) != 0L) return null // b5==0 -> w register (op_str 'w0,', not 'x0,')
    if (rd(w) != 0L) return null // Rt == w0
    var imm = (w ushr 5) and 0x3FFF
    if (imm and 0x2000L != 0L) imm -= 0x4000L
    return pc + imm * 4
}

/** `ldr wN,[xBase,#off]` (32-bit, unsigned-offset) -> Triple(N, baseReg, off); else null. */
internal fun ldrWImm(w: Long): Triple<Int, Int, Long>? = memWImm(w, LDR_W_UIMM)

/** `str wN,[xBase,#off]` (32-bit, unsigned-offset) -> Triple(N, baseReg, off); else null. */
internal fun strWImm(w: Long): Triple<Int, Int, Long>? = memWImm(w, STR_W_UIMM)

private fun memWImm(w: Long, opc: Long): Triple<Int, Int, Long>? {
    if ((w and MEM_W_UIMM_MASK) != opc) return null
    val rt = rd(w).toInt()
    val base = rn(w).toInt()
    val off = ((w ushr 10) and 0xFFF) shl 2 // 32-bit scale
    return Triple(rt, base, off)
}

/** The committed-mode read feeds a multi-way dispatch: cbz/cbnz wReg, or cmp wReg,#imm. */
internal fun dispatchesOn(w: Long, reg: Int): Boolean {
    val top = w and COMPARE_BRANCH_MASK
    if ((top == CBZ || top == CBNZ) && (w ushr 31) == 0L && rd(w).toInt() == reg) return true
    // cmp wReg,#imm == subs wzr, wReg, #imm (32-bit)
    return (w and SUBS_IMM_MASK) == SUBS_IMM && rd(w) == 31L && rn(w).toInt() == reg
}

/**
 * A load whose base register is x0 (unsigned-immediate integer load, or load-pair). Used to spot
 * host_package_name()'s result being dereferenced as a &cached-member pointer right after its bl.
 */
internal fun isLoadBaseX0(w: Long): Boolean {
    if (rn(w) != 0L) return false
    // unsigned-offset load/store immediate family; loads have opc(22-23) != 00.
    if ((w and LDST_UIMM_MASK) == LDST_UIMM && ((w ushr 22) and 3L) != 0L) return true
    // load/store pair; loads have L (bit 22) set.
    if ((w and LDST_PAIR_MASK) == LDST_PAIR && (w and LDST_PAIR_LOAD_BIT) != 0L) return true
    return false
}

/** `movz wReg,#0` as a little-endian 4-byte hex string (the mode-era patch). */
internal fun movzWnZero(reg: Int): String {
    val insn = MOVZ_W or (reg.toLong() and 0x1F)
    val b = ByteArray(4)
    b[0] = (insn and 0xFF).toByte()
    b[1] = ((insn ushr 8) and 0xFF).toByte()
    b[2] = ((insn ushr 16) and 0xFF).toByte()
    b[3] = ((insn ushr 24) and 0xFF).toByte()
    return b.toHexString()
}
