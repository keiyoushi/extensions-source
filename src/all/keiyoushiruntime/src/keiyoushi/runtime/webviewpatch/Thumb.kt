package keiyoushi.runtime.webviewpatch

/*
 * Shared arm32 (Thumb / Thumb-2) machine-code decode layer for the XRW locator.
 *
 * Thumb is variable-width: a halfword is a 16-bit instruction unless it opens a 32-bit one. These are
 * the narrow forms the locate needs — the 16/32 width test, MOVW/MOVT, PC-relative LDR (T1 16-bit +
 * T2 literal pool), ADD rD,pc (the PIC site), BL/BLX (split immediate), BX lr / POP{..,pc} / MOV pc,lr,
 * CBZ/CBNZ, BEQ/BNE, ADD(imm), MVN(imm), PUSH{..,lr} — as named encodings plus bit-exact predicates and
 * extractors (halfword vs word handling, sign-extension and PC alignment preserved), plus the
 * LDR/STR(imm), CMP and zeroing-MOV forms the mode-era chain needs. [ThumbLocator] and [ThumbModeLocator]
 * are the consumers. Halfwords are plain Int; immediates are Long where a 32-bit value is formed.
 */

// ---- instruction width ----

/** Top 5 bits of a halfword select its instruction group. */
internal const val THUMB_HW_GROUP_MASK = 0xF800

/** The three leading halfword groups (0b11101/0b11110/0b11111) that begin a 32-bit Thumb-2 instruction. */
internal const val THUMB32_GROUP_A = 0xE800
internal const val THUMB32_GROUP_B = 0xF000
internal const val THUMB32_GROUP_C = 0xF800

/** A halfword begins a 32-bit instruction; otherwise it is a standalone 16-bit instruction. */
internal fun isThumb32(hw1: Int): Boolean = (hw1 and THUMB_HW_GROUP_MASK).let {
    it == THUMB32_GROUP_A || it == THUMB32_GROUP_B || it == THUMB32_GROUP_C
}

// ---- instruction encodings (value) and their decode masks ----

/** BL/BLX second halfword: the (1:1:J1:1:J2) marker, and the BL-vs-BLX bit within it. */
internal const val BL_HW2_MASK = 0xC000
internal const val BL_HW2 = 0xC000

/** BX lr. */
internal const val BX_LR = 0x4770

/** POP {..,pc} (T1 16-bit). */
internal const val POP_PC_T1_MASK = 0xFF00
internal const val POP_PC_T1 = 0xBD00

/** POP {..,pc} (T2 32-bit, LDMIA sp!): first halfword, and the pc bit in the register list. */
internal const val POP_PC_W_HW1 = 0xE8BD
internal const val POP_PC_W_PC_BIT = 0x8000

/** MOV pc, lr. */
internal const val MOV_PC_LR = 0x46F7

/** MVN (immediate), 32-bit. */
internal const val MVN_IMM_MASK = 0xFBEF
internal const val MVN_IMM = 0xF06F

/** ADDS Rd,#imm8 (T2 16-bit); Rd in bits 8..10. */
internal const val ADDS_IMM8_MASK = 0xF800
internal const val ADDS_IMM8 = 0x3000

/** ADDS Rd,Rn,#imm3 (T1 16-bit); Rd in bits 0..2. */
internal const val ADDS_IMM3_MASK = 0xFE00
internal const val ADDS_IMM3 = 0x1C00

/** ADDW Rd,Rn,#imm12 (T4, direct 12-bit immediate). */
internal const val ADDW_T4_MASK = 0xFBF0
internal const val ADDW_T4 = 0xF200

/** ADD.W Rd,Rn,#const (T3, ThumbExpandImm). */
internal const val ADD_W_T3_MASK = 0xFBE0
internal const val ADD_W_T3 = 0xF100

/** MOVW Rd,#imm16 (32-bit). */
internal const val MOVW_MASK = 0xFBF0
internal const val MOVW = 0xF240

/** MOVT Rd,#imm16 (32-bit). */
internal const val MOVT_MASK = 0xFBF0
internal const val MOVT = 0xF2C0

/** LDR Rt,[pc,#imm] literal pool, T1 16-bit. */
internal const val LDR_LIT_T1_MASK = 0xF800
internal const val LDR_LIT_T1 = 0x4800

/** LDR Rt,[pc,#imm] literal pool, T2 32-bit (U bit = add/subtract). */
internal const val LDR_LIT_T2_MASK = 0xFF7F
internal const val LDR_LIT_T2 = 0xF85F

/** ADD Rd,pc (the PIC base site): Rd 0..7 form, Rd 8..15 form, and the register-group mask. */
internal const val ADD_PC_MASK = 0xFFF8
internal const val ADD_PC_LO = 0x4478
internal const val ADD_PC_HI = 0x44F8

/** CBZ / CBNZ (16-bit). THUMB_ prefix keeps these clear of the arm64 CBZ/CBNZ encodings. */
internal const val CBZ_CBNZ_MASK = 0xF500
internal const val THUMB_CBZ = 0xB100
internal const val THUMB_CBNZ = 0xB900

/** `b label` (T2 16-bit unconditional branch). */
internal const val UNCOND_B_T2 = 0xE000

/** B<cond> (16-bit); the committed guard set is b.eq/b.ne. THUMB_ prefix keeps clear of arm64 B_COND. */
internal const val THUMB_B_COND_MASK = 0xF000
internal const val THUMB_B_COND = 0xD000
internal const val COND_EQ = 0
internal const val COND_NE = 1

/** PUSH {..,lr} (T1 16-bit). */
internal const val PUSH_LR_T1_MASK = 0xFF00
internal const val PUSH_LR_T1 = 0xB500

/** PUSH {..,lr} (T2 32-bit, STMDB sp!): first halfword, and the lr bit in the register list. */
internal const val PUSH_LR_W_HW1 = 0xE92D
internal const val PUSH_LR_W_LR_BIT = 0x4000

/** Rn == sp / Rn == pc (the bases an adder's ADD(imm) must not use). */
internal const val THUMB_REG_SP = 13
internal const val THUMB_REG_PC = 15

/** Thumb bx-lr, the arm32 patch window (little-endian halfword 0x4770). */
internal const val THUMB_BX_LR_PATCH = "7047"

// ---- predicates and extractors ----

/** BL (not BLX): a 32-bit instruction whose two halfwords carry the BL marker. */
internal fun isThumbBl(size: Int, hw1: Int, hw2: Int): Boolean = size == 4 && (hw1 and THUMB_HW_GROUP_MASK) == THUMB32_GROUP_B && (hw2 and BL_HW2_MASK) == BL_HW2

/** A function return at this instruction: bx lr, pop {..,pc}, or mov pc,lr. */
internal fun isThumbRet(size: Int, hw1: Int, hw2: Int): Boolean = (size == 2 && hw1 == BX_LR) ||
    (size == 2 && (hw1 and POP_PC_T1_MASK) == POP_PC_T1) ||
    (size == 4 && hw1 == POP_PC_W_HW1 && (hw2 and POP_PC_W_PC_BIT) != 0) ||
    (size == 2 && hw1 == MOV_PC_LR)

/** MVN (immediate), 32-bit — the error-sentinel move the inlined-setter reject looks for. */
internal fun isThumbMvnImm(size: Int, hw1: Int): Boolean = size == 4 && (hw1 and MVN_IMM_MASK) == MVN_IMM

/** `add r0, {#imm | rN,#imm}` (Rd==0, base not sp/pc) -> its immediate (unfiltered), else null. */
internal fun thumbAddR0Imm(size: Int, hw1: Int, hw2: Int): Long? {
    if (size == 2) {
        if ((hw1 and ADDS_IMM8_MASK) == ADDS_IMM8 && ((hw1 ushr 8) and 7) == 0) return (hw1 and 0xFF).toLong() // adds r0,#imm8
        if ((hw1 and ADDS_IMM3_MASK) == ADDS_IMM3 && (hw1 and 7) == 0) return ((hw1 ushr 6) and 7).toLong() // adds r0,rN,#imm3
        return null
    }
    val rd = (hw2 ushr 8) and 0xF
    val rn = hw1 and 0xF
    if (rd != 0 || rn == THUMB_REG_SP || rn == THUMB_REG_PC) return null
    val i = (hw1 ushr 10) and 1
    val imm3 = (hw2 ushr 12) and 7
    val imm8 = hw2 and 0xFF
    if ((hw1 and ADDW_T4_MASK) == ADDW_T4) return ((i shl 11) or (imm3 shl 8) or imm8).toLong() // ADDW T4 (direct imm12)
    if ((hw1 and ADD_W_T3_MASK) == ADD_W_T3) return thumbExpandImm((i shl 11) or (imm3 shl 8) or imm8) // ADD.W T3 (expand)
    return null
}

/** A conditional guard the committed-mode features() recognises: cbz/cbnz, or b.eq/b.ne. */
internal fun isThumbCond(size: Int, hw1: Int): Boolean {
    if (size != 2) return false
    if ((hw1 and CBZ_CBNZ_MASK) == THUMB_CBZ || (hw1 and CBZ_CBNZ_MASK) == THUMB_CBNZ) return true // cbz/cbnz
    if ((hw1 and THUMB_B_COND_MASK) == THUMB_B_COND) {
        val c = (hw1 ushr 8) and 0xF
        return c == COND_EQ || c == COND_NE
    } // beq/bne
    return false
}

/** ThumbExpandImm(imm12): the A6.3.2 constant expansion (replicate / rotated 8-bit). */
internal fun thumbExpandImm(imm12: Int): Long {
    val t2 = (imm12 ushr 10) and 3
    val imm8 = imm12 and 0xFF
    if (t2 == 0) {
        return when ((imm12 ushr 8) and 3) {
            0 -> imm8.toLong()
            1 -> ((imm8.toLong() shl 16) or imm8.toLong())
            2 -> ((imm8.toLong() shl 24) or (imm8.toLong() shl 8))
            else -> ((imm8.toLong() shl 24) or (imm8.toLong() shl 16) or (imm8.toLong() shl 8) or imm8.toLong())
        }
    }
    val unrot = (0x80 or (imm12 and 0x7F))
    val rot = (imm12 ushr 7) and 0x1F
    return (((unrot ushr rot) or (unrot shl (32 - rot))).toLong() and 0xFFFFFFFFL)
}

/** MOVW's 16-bit immediate if this is a MOVW, else null. */
internal fun thumbMovwImm16(size: Int, hw1: Int, hw2: Int): Int? {
    if (size != 4 || (hw1 and MOVW_MASK) != MOVW) return null
    return thumbMovImm16(hw1, hw2)
}

/** MOVT's 16-bit immediate if this is a MOVT, else null. */
internal fun thumbMovtImm16(size: Int, hw1: Int, hw2: Int): Int? {
    if (size != 4 || (hw1 and MOVT_MASK) != MOVT) return null
    return thumbMovImm16(hw1, hw2)
}

/** The 16-bit immediate assembled from a MOVW/MOVT (imm4:i:imm3:imm8). */
internal fun thumbMovImm16(hw1: Int, hw2: Int): Int {
    val imm4 = hw1 and 0xF
    val i = (hw1 ushr 10) and 1
    val imm3 = (hw2 ushr 12) and 7
    val imm8 = hw2 and 0xFF
    return (imm4 shl 12) or (i shl 11) or (imm3 shl 8) or imm8
}

/** The destination register of a MOVW/MOVT. */
internal fun thumbMovwtRd(hw2: Int): Int = (hw2 ushr 8) and 0xF

/**
 * A PC-relative literal load -> Pair(Rt, literal-pool VA), or null. Covers the T1 16-bit `ldr rt,[pc,#]`
 * and the T2 32-bit form; the caller reads the word at the returned VA. PC base is word-aligned (pc+4).
 */
internal fun thumbLdrLiteralAddr(size: Int, hw1: Int, hw2: Int, addr: Long): Pair<Int, Long>? {
    if (size == 2 && (hw1 and LDR_LIT_T1_MASK) == LDR_LIT_T1) {
        val rt = (hw1 ushr 8) and 7
        val imm8 = hw1 and 0xFF
        val a = ((addr + 4) and 3L.inv()) + imm8 * 4
        return rt to a
    }
    if (size == 4 && (hw1 and LDR_LIT_T2_MASK) == LDR_LIT_T2) {
        val u = (hw1 ushr 7) and 1
        val rt = (hw2 ushr 12) and 0xF
        val imm12 = hw2 and 0xFFF
        val disp = if (u == 1) imm12 else -imm12
        val a = ((addr + 4) and 3L.inv()) + disp
        return rt to a
    }
    return null
}

/** `add rD, pc` (the PIC base). */
internal fun isThumbAddPc(hw1: Int): Boolean = (hw1 and ADD_PC_MASK) == ADD_PC_LO || (hw1 and ADD_PC_MASK) == ADD_PC_HI

/** The destination register of an `add rD,pc` (low form 0..7, high form 8..15). */
internal fun thumbAddPcRd(hw1: Int): Int = (hw1 and 7) or (if (hw1 and 0x80 != 0) 8 else 0)

/** `push {..,lr}` T1 16-bit prologue. */
internal fun isThumbPushLrT1(hw1: Int): Boolean = (hw1 and PUSH_LR_T1_MASK) == PUSH_LR_T1

/**
 * Absolute target of a BL/BLX pair at [pc] (split sign-extended immediate; BLX word-aligns the base),
 * or null if the two halfwords are not a BL/BLX. [pc] is the VA of the first halfword.
 */
internal fun thumbBlTarget(hw1: Int, hw2: Int, pc: Long): Long? {
    if ((hw1 and THUMB_HW_GROUP_MASK) != THUMB32_GROUP_B || (hw2 and BL_HW2_MASK) != BL_HW2) return null
    val s = (hw1 ushr 10) and 1
    val imm10 = hw1 and 0x3FF
    val j1 = (hw2 ushr 13) and 1
    val j2 = (hw2 ushr 11) and 1
    val isBl = (hw2 ushr 12) and 1
    val imm11 = hw2 and 0x7FF
    val i1 = 1 - (j1 xor s)
    val i2 = 1 - (j2 xor s)
    var imm = ((s shl 24) or (i1 shl 23) or (i2 shl 22) or (imm10 shl 12) or (imm11 shl 1)).toLong()
    if (imm and (1L shl 24) != 0L) imm -= (1L shl 25)
    val base = pc + 4
    return if (isBl == 1) (base + imm) and 0xFFFFFFFFL else ((base and 3L.inv()) + imm) and 0xFFFFFFFFL
}

// ---- mode-era (committed-mode read) forms, read only by ThumbModeLocator ----

/** LDR/STR Rt,[Rn,#imm5*4] (T1 16-bit): opcode bits, and the 32-bit LDR.W/STR.W Rt,[Rn,#imm12] (T3) first halfword. */
internal const val LDR_IMM_T1 = 0x6800
internal const val STR_IMM_T1 = 0x6000
internal const val LDST_IMM_T1_MASK = 0xF800
internal const val LDR_IMM_T3 = 0xF8D0
internal const val STR_IMM_T3 = 0xF8C0
internal const val LDST_IMM_T3_MASK = 0xFFF0

/** CMP Rn,#imm8 (T1 16-bit); Rn in bits 8..10. */
internal const val CMP_IMM_T1_MASK = 0xF800
internal const val CMP_IMM_T1 = 0x2800

/** CBZ (not CBNZ): the op bit is clear. */
internal const val CBZ_ONLY_MASK = 0xFD00

/** MOVS Rd,#0 (T1 16-bit) and MOV.W Rd,#0 (T2 32-bit, S=0) — the zeroing patches. */
internal const val MOVS_IMM_T1 = 0x2000
internal const val MOV_W_IMM_HW1 = 0xF04F

/** A 16-bit LDR (T1) or 32-bit LDR.W (T3) with an immediate offset -> Triple(Rt, Rn, byte offset), else null. */
internal fun thumbLdrImm(size: Int, hw1: Int, hw2: Int): Triple<Int, Int, Long>? = thumbLdstImm(size, hw1, hw2, LDR_IMM_T1, LDR_IMM_T3)

/** A 16-bit STR (T1) or 32-bit STR.W (T3) with an immediate offset -> Triple(Rt, Rn, byte offset), else null. */
internal fun thumbStrImm(size: Int, hw1: Int, hw2: Int): Triple<Int, Int, Long>? = thumbLdstImm(size, hw1, hw2, STR_IMM_T1, STR_IMM_T3)

private fun thumbLdstImm(size: Int, hw1: Int, hw2: Int, t1: Int, t3: Int): Triple<Int, Int, Long>? {
    if (size == 2 && (hw1 and LDST_IMM_T1_MASK) == t1) {
        return Triple(hw1 and 7, (hw1 ushr 3) and 7, ((hw1 ushr 6) and 0x1F) * 4L)
    }
    if (size == 4 && (hw1 and LDST_IMM_T3_MASK) == t3 && (hw1 and 0xF) != THUMB_REG_PC) {
        return Triple((hw2 ushr 12) and 0xF, hw1 and 0xF, (hw2 and 0xFFF).toLong())
    }
    return null
}

/** `add.w/addw r0, Rn, #imm` (32-bit, Rn not sp/pc) -> Pair(Rn, imm), else null. */
internal fun thumbAddR0RnImm(size: Int, hw1: Int, hw2: Int): Pair<Int, Long>? {
    if (size != 4 || ((hw2 ushr 8) and 0xF) != 0) return null
    val rn = hw1 and 0xF
    if (rn == THUMB_REG_SP || rn == THUMB_REG_PC) return null
    val imm12 = (((hw1 ushr 10) and 1) shl 11) or (((hw2 ushr 12) and 7) shl 8) or (hw2 and 0xFF)
    if ((hw1 and ADDW_T4_MASK) == ADDW_T4) return rn to imm12.toLong()
    if ((hw1 and ADDW_T4_MASK) == ADD_W_T3) return rn to thumbExpandImm(imm12) // ADD.W T3 with S=0
    return null
}

/** `cbz r0, label` (16-bit) at [pc] -> absolute target VA, else null. */
internal fun thumbCbzR0Target(size: Int, hw1: Int, pc: Long): Long? {
    if (size != 2 || (hw1 and CBZ_ONLY_MASK) != THUMB_CBZ || (hw1 and 7) != 0) return null
    return pc + 4 + ((((hw1 ushr 9) and 1) shl 6) or (((hw1 ushr 3) and 0x1F) shl 1))
}

/** `cmp rN, #imm8` (T1 16-bit) on [reg]. */
internal fun isThumbCmpImmOn(size: Int, hw1: Int, reg: Int): Boolean = size == 2 && (hw1 and CMP_IMM_T1_MASK) == CMP_IMM_T1 && ((hw1 ushr 8) and 7) == reg

/** `cbnz r0, label` (16-bit, not cbz) at [pc] -> absolute target VA, else null. */
internal fun thumbCbnzR0Target(size: Int, hw1: Int, pc: Long): Long? {
    if (size != 2 || (hw1 and CBZ_ONLY_MASK) != THUMB_CBNZ || (hw1 and 7) != 0) return null
    return pc + 4 + ((((hw1 ushr 9) and 1) shl 6) or (((hw1 ushr 3) and 0x1F) shl 1))
}

/**
 * The 16-bit unconditional branch `b target` (T2) from [pc], as little-endian hex, or null if the signed
 * 11-bit displacement would not reach. Used to turn a conditional guard into an always-taken branch.
 */
internal fun thumbForceBranch(pc: Long, target: Long): String? {
    val imm11 = ((target - (pc + 4)) / 2).toInt()
    if (imm11 < -0x400 || imm11 >= 0x400) return null
    val hw = UNCOND_B_T2 or (imm11 and 0x7FF)
    return byteArrayOf((hw and 0xFF).toByte(), ((hw ushr 8) and 0xFF).toByte()).toHexString()
}

/** `movs rN,#0` (16-bit) or `mov.w rN,#0` (32-bit, flags untouched) as little-endian hex, matching the read's width. */
internal fun thumbZeroReg(reg: Int, size: Int): String {
    val halfwords = if (size == 2) intArrayOf(MOVS_IMM_T1 or (reg shl 8)) else intArrayOf(MOV_W_IMM_HW1, reg shl 8)
    val b = ByteArray(halfwords.size * 2)
    halfwords.forEachIndexed { i, h ->
        b[2 * i] = (h and 0xFF).toByte()
        b[2 * i + 1] = ((h ushr 8) and 0xFF).toByte()
    }
    return b.toHexString()
}
