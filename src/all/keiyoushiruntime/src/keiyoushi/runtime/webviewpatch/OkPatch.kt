package keiyoushi.runtime.webviewpatch

/**
 * A promoted patch with its undo window: `ok:<adderVa>:<verifyVa>:<verifyHex>:<patchHex>`. It is plain data,
 * not tied to the locator version that wrote it: the Keiyoushi Runtime is the one copy of this code in the
 * process, so there is never a second copy at another version to negotiate with, and a cold start just
 * re-applies whatever the store holds. A change to the record's fields or meaning takes a new prefix, which an
 * older runtime then reads as corrupt.
 */
internal class OkPatch(
    val adderVa: Long,
    val verifyVa: Long,
    val verifyHex: String,
    val patchHex: String,
) {
    fun encode(): String = "ok:${adderVa.toString(16)}:${verifyVa.toString(16)}:$verifyHex:$patchHex"

    companion object {
        /** Parses an `ok:` record; null for anything malformed. */
        fun parse(raw: String): OkPatch? {
            val parts = raw.split(':')
            if (parts.size != 5 || parts[0] != "ok") return null
            val adder = parts[1].toLongOrNull(16) ?: return null
            val verify = parts[2].toLongOrNull(16) ?: return null
            val window = parts[3]
            val patch = parts[4]
            if (verify < 0 || adder < 0) return null
            if (window.length != VERIFY_WINDOW * 2 || patch.length !in setOf(4, 8)) return null
            if ((window + patch).any { it !in "0123456789abcdef" }) return null
            return OkPatch(adder, verify, window, patch)
        }
    }
}
