package keiyoushi.lib.publus

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import kotlin.time.Duration

class PublusAuth(
    val hti: String? = null,
    val cfg: String? = null,
    val bid: String? = null,
    val uuid: String? = null,
    val pfCd: String? = null,
    val policy: String? = null,
    val signature: String? = null,
    val keyPairId: String? = null,
) {
    /** Appends every non-null parameter to [builder] and returns it for chaining. */
    fun applyTo(builder: HttpUrl.Builder): HttpUrl.Builder = builder.apply {
        hti?.let { addQueryParameter("hti", it) }
        cfg?.let { addQueryParameter("cfg", it) }
        bid?.let { addQueryParameter("BID", it) }
        uuid?.let { addQueryParameter("uuid", it) }
        pfCd?.let { addQueryParameter("pfCd", it) }
        policy?.let { addQueryParameter("Policy", it) }
        signature?.let { addQueryParameter("Signature", it) }
        keyPairId?.let { addQueryParameter("Key-Pair-Id", it) }
    }
}

/**
 * The CloudFront `auth_info` block returned by a Publus content API.
 */
@Serializable
class PublusAuthInfo(
    val hti: String?,
    val cfg: Int?,
    val uuid: String?,
    val pfCd: String?,
    @SerialName("Policy") val policy: String?,
    @SerialName("Signature") val signature: String?,
    @SerialName("Key-Pair-Id") val keyPairId: String?,
) {
    /**
     * Converts the raw auth block into the [PublusAuth] applied to requests.
     *
     * @param bid value for the `BID` query parameter.
     * @param includeBookAuth when false the book-scoped parameters (`hti`, `cfg`, `uuid`, `BID`)
     * are dropped and only the CloudFront signature is kept.
     */
    fun toAuth(bid: String? = null, includeBookAuth: Boolean = true) = PublusAuth(
        hti = if (includeBookAuth) hti else null,
        cfg = if (includeBookAuth) cfg?.toString() else null,
        bid = if (includeBookAuth) bid else null,
        uuid = if (includeBookAuth) uuid else null,
        pfCd = pfCd,
        policy = policy,
        signature = signature,
        keyPairId = keyPairId,
    )
}

/**
 * The shared shape of a Publus content/auth API response.
 * [url] is null when the content is not available.
 */
@Serializable
class PublusContent(
    val url: String?,
    val cty: Int?,
    @SerialName("auth_info") val authInfo: PublusAuthInfo?,
)

/**
 * Caches per-chapter [PublusAuth] and refreshes it once it gets older than [refreshInterval], so
 * signed image URLs stay valid for the lifetime of a reading session.
 *
 * @param refreshInterval how long auth parameters are used before a refresh is attempted.
 * @param refresh fetches fresh auth from the content API for a page's session map.
 */
class PublusAuthHandler(
    private val refreshInterval: Duration,
    private val refresh: suspend (session: Map<String, String>) -> PublusAuth?,
) {
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<PublusAuth, Long>>()

    /** Seeds the cache with auth obtained while building the page list. */
    suspend fun store(key: String, auth: PublusAuth) = mutex.withLock {
        cache[key] = auth to System.currentTimeMillis()
    }

    /**
     * Returns the cached auth for [key], refreshing it with [session] when it is stale. Falls back
     * to the stale auth when the refresh returns nothing, and returns null only if nothing is cached.
     */
    suspend fun currentAuth(key: String, session: Map<String, String>): PublusAuth? = mutex.withLock {
        val cached = cache[key]
        if (cached != null && System.currentTimeMillis() - cached.second < refreshInterval.inWholeMilliseconds) {
            return@withLock cached.first
        }

        val auth = refresh(session) ?: return@withLock cached?.first
        cache[key] = auth to System.currentTimeMillis()
        auth
    }
}
