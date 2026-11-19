package eu.kanade.tachiyomi.extension.es.aedex

import keiyoushi.utils.hmacSha256
import keiyoushi.utils.sha256

// Proofs of the site's readers, derived like their own clients do: seed = sha256("t:s"), key = sha256("seed:p"),
// and the header names, build id and live path are cut from the seed.
// The prefix tells the readers apart: "ax" for the chapter page's, "bv" for the image host's.
class ReaderSigner(prefix: String, t: String, s: String) {
    private val seed = "$t:$s".sha256().toHexString()
    private val key = "$seed:p".sha256()

    private val proofName = "$prefix-sig-${seed.substring(7, 15)}"
    val liveName = "$prefix-live-${seed.substring(15, 23)}"
    val buildName = "$prefix-build-${seed.substring(23, 31)}"
    val buildId = seed.substring(31, 43)
    val livePath = seed.substring(43, 51)

    // Proof over "scope.subject.time", e.g. ("c", "slug/chapter") for a chapter and ("p", handle) for a page
    fun proof(scope: String, subject: String, time: Long): Pair<String, String> {
        val signature = "$scope.$subject.$time".hmacSha256(key).toHexString().take(SIGNATURE_LENGTH)
        return proofName to "$time.$signature"
    }

    private companion object {
        const val SIGNATURE_LENGTH = 40
    }
}
