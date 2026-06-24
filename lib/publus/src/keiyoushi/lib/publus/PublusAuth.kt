package keiyoushi.lib.publus

import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

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
    val hti: String? = null,
    val cfg: Int? = null,
    val uuid: String? = null,
    val pfCd: String? = null,
    @SerialName("Policy") val policy: String? = null,
    @SerialName("Signature") val signature: String? = null,
    @SerialName("Key-Pair-Id") val keyPairId: String? = null,
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
 */
@Serializable
class PublusContent(
    val status: String?,
    val url: String?,
    val lp: Int?,
    val cty: Int? = null,
    val lin: Int?,
    val lpd: Int?,
    val bs: Int?,
    val ms: Int?,
    @SerialName("auth_info") val authInfo: PublusAuthInfo? = null,
)

/**
 * Caches per-chapter [PublusAuth] and transparently refreshes it from the content API, so signed
 * image URLs stay valid for the lifetime of a reading session.
 *
 * @param client client used to refresh auth.
 * @param refreshSeconds how long auth parameters stay valid before a refresh is attempted.
 * @param cookieUrl when set, cookies for this URL are loaded and handed to [buildRequest].
 * @param bid value forwarded to [PublusAuthInfo.toAuth] when building the refreshed auth.
 * @param buildRequest builds the refresh request from a page's session map and the loaded cookies.
 */
class PublusAuthHandler(
    private val client: OkHttpClient,
    private val refreshSeconds: Long,
    private val cookieUrl: HttpUrl? = null,
    private val bid: String? = null,
    private val buildRequest: (session: Map<String, String>, cookies: List<Cookie>) -> Request,
) {
    private val cache = ConcurrentHashMap<String, Pair<PublusAuth, Long>>()

    /** Seeds the cache with auth obtained while building the page list. */
    fun store(key: String, auth: PublusAuth) {
        cache[key] = auth to System.currentTimeMillis()
    }

    /**
     * Returns the cached auth for [key], refreshing it via the configured request when it is stale.
     * [session] is forwarded to the request builder. Returns null only if nothing is cached and the
     * refresh fails.
     */
    fun currentAuth(key: String, session: Map<String, String>): PublusAuth? {
        cache[key]?.let { (auth, time) ->
            if (!isStale(time)) return auth
        }
        synchronized(cache) {
            cache[key]?.let { (auth, time) ->
                if (!isStale(time)) return auth
            }
            runCatching { refresh(session) }.getOrNull()?.let {
                cache[key] = it to System.currentTimeMillis()
                return it
            }
            return cache[key]?.first
        }
    }

    private fun refresh(session: Map<String, String>): PublusAuth? {
        val cookies = cookieUrl?.let { client.cookieJar.loadForRequest(it) }.orEmpty()
        return client.newCall(buildRequest(session, cookies))
            .execute()
            .parseAs<PublusContent>()
            .authInfo
            ?.toAuth(bid = bid)
    }

    private fun isStale(time: Long) = System.currentTimeMillis() - time > refreshSeconds * 1000
}
