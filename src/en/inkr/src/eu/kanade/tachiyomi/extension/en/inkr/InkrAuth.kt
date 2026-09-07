package eu.kanade.tachiyomi.extension.en.inkr

import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class InkrAuth(
    private val client: () -> OkHttpClient,
    private val baseUrl: () -> String,
) {
    private val mutex = Mutex()

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    private var refreshToken: String? = null

    @Volatile
    private var firebaseApiKey: String? = null

    @Volatile
    private var expiresAtMs: Long = 0

    @Volatile
    var isSubscriber: Boolean = false
        private set

    @Volatile
    private var paymentLoaded: Boolean = false

    suspend fun ensureLoaded() = mutex.withLock {
        val now = System.currentTimeMillis()
        when {
            accessToken != null && now < expiresAtMs - 30.seconds.inWholeMilliseconds -> Unit
            refreshToken != null && refreshAccessToken() -> Unit
            else -> loadFromWebView()
        }

        if (accessToken != null) {
            if (!paymentLoaded) {
                isSubscriber = fetchIsSubscriber()
                paymentLoaded = true
            }
        } else {
            isSubscriber = false
            paymentLoaded = false
        }
    }

    private fun clearSession() {
        accessToken = null
        refreshToken = null
        firebaseApiKey = null
        expiresAtMs = 0
        isSubscriber = false
        paymentLoaded = false
    }

    private suspend fun loadFromWebView() {
        val stored = readAuthUser() ?: run {
            clearSession()
            return
        }
        val user = runCatching { stored.value.parseAs<FirebaseAuthUserDto>() }.getOrNull()
        val sts = user?.stsTokenManager
        if (sts == null || sts.accessToken.isEmpty()) {
            clearSession()
            return
        }
        accessToken = sts.accessToken
        refreshToken = sts.refreshToken.takeIf { it.isNotEmpty() }
        firebaseApiKey = user.apiKey.takeIf { it.isNotEmpty() }
            ?: apiKeyFromStorageKey(stored.key)
        expiresAtMs = when {
            sts.expirationTime > 1_000_000_000_000L -> sts.expirationTime
            sts.expirationTime > 0L -> sts.expirationTime * 1000
            else -> System.currentTimeMillis() + 50.minutes.inWholeMilliseconds
        }
        if (System.currentTimeMillis() >= expiresAtMs - 30.seconds.inWholeMilliseconds && refreshToken != null) {
            refreshAccessToken()
        }
        paymentLoaded = false
    }

    private suspend fun fetchIsSubscriber(): Boolean {
        val token = accessToken ?: return false
        readIsSubscriber("Bearer $token")?.let { return it }
        return readIsSubscriber(token) ?: false
    }

    private suspend fun readIsSubscriber(authorization: String): Boolean? {
        val headers = Headers.Builder()
            .set("Authorization", authorization)
            .set("ikc-platform", "web")
            .set("Accept", "application/json")
            .build()
        val response = runCatching {
            client().get("https://inkr-payment-api.inkr.com/v1/user/my-info", headers)
        }.getOrNull() ?: return null

        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val envelope = runCatching { response.parseAs<PaymentInfoEnvelope>() }.getOrNull()
        if (envelope != null) {
            return envelope.data?.isSubscriber ?: envelope.isSubscriber
        }
        return null
    }

    private suspend fun readAuthUser(): StoredAuthUser? = runCatching {
        runWebView<StoredAuthUser?>(timeout = 15.seconds) {
            domStorageEnabled = true
            jsBridge(BRIDGE_NAME) { message ->
                resolve(
                    message.takeUnless { it.isEmpty() }
                        ?.let { runCatching { it.parseAs<StoredAuthUser>() }.getOrNull() },
                )
            }
            onPageFinished {
                evaluateJs(READ_AUTH_JS)
            }
            loadData(baseUrl(), "")
        }
    }.getOrNull()

    private suspend fun refreshAccessToken(): Boolean {
        val token = refreshToken ?: return false
        val apiKey = firebaseApiKey ?: return false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", token)
            .build()
        val headers = Headers.Builder()
            .set("Content-Type", "application/x-www-form-urlencoded")
            .build()
        val url = "https://securetoken.googleapis.com/v1/token".toHttpUrl().newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        val response = runCatching {
            client().post(url, headers, body).parseAs<TokenRefreshDto>()
        }.getOrNull() ?: return false

        if (response.idToken.isEmpty()) return false
        accessToken = response.idToken
        if (response.refreshToken.isNotEmpty()) {
            refreshToken = response.refreshToken
        }
        val expiresIn = response.expiresIn.toLongOrNull()?.seconds ?: 3600.seconds
        expiresAtMs = System.currentTimeMillis() + expiresIn.inWholeMilliseconds
        return true
    }

    companion object {
        private const val BRIDGE_NAME = "inkrAuthBridge"
        private val STORAGE_KEY_API_KEY = Regex("""^firebase:authUser:([^:]+):""")

        private fun apiKeyFromStorageKey(key: String): String? = STORAGE_KEY_API_KEY.find(key)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }

        private val READ_AUTH_JS = """
            (async () => {
              const post = (key, value) => {
                if (value == null) {
                  window.$BRIDGE_NAME.post("");
                  return;
                }
                window.$BRIDGE_NAME.post(JSON.stringify({ key: key || "", value: String(value) }));
              };
              const unwrap = (entry) => {
                if (entry == null) return null;
                const value = typeof entry === "string"
                  ? entry
                  : (entry.value != null ? entry.value : entry);
                return typeof value === "string" ? value : JSON.stringify(value);
              };
              try {
                for (let i = 0; i < localStorage.length; i++) {
                  const key = localStorage.key(i);
                  if (key && key.indexOf("firebase:authUser:") === 0) {
                    const fromLs = localStorage.getItem(key);
                    if (fromLs) {
                      post(key, fromLs);
                      return;
                    }
                  }
                }
                const db = await new Promise((resolve, reject) => {
                  const req = indexedDB.open("firebaseLocalStorageDb");
                  req.onerror = () => reject(req.error);
                  req.onsuccess = () => resolve(req.result);
                });
                if (!db.objectStoreNames.contains("firebaseLocalStorage")) {
                  db.close();
                  post(null, null);
                  return;
                }
                const entries = await new Promise((resolve, reject) => {
                  const tx = db.transaction("firebaseLocalStorage", "readonly");
                  const store = tx.objectStore("firebaseLocalStorage");
                  const req = store.getAll();
                  req.onerror = () => reject(req.error);
                  req.onsuccess = () => resolve(req.result || []);
                });
                db.close();
                for (const entry of entries) {
                  const key = entry && (entry.fbase_key || entry.key);
                  if (key && String(key).indexOf("firebase:authUser:") === 0) {
                    const raw = unwrap(entry);
                    if (raw) {
                      post(String(key), raw);
                      return;
                    }
                  }
                }
                post(null, null);
              } catch (e) {
                post(null, null);
              }
            })();
        """.trimIndent()
    }
}

@Serializable
private class StoredAuthUser(
    val key: String = "",
    val value: String = "",
)

@Serializable
private class FirebaseAuthUserDto(
    val apiKey: String = "",
    val stsTokenManager: StsTokenManagerDto? = null,
)

@Serializable
private class StsTokenManagerDto(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expirationTime: Long = 0,
)

@Serializable
private class TokenRefreshDto(
    @SerialName("id_token") val idToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: String = "3600",
)

@Serializable
private class PaymentInfoEnvelope(
    val code: Int = 0,
    val data: PaymentInfoDto? = null,
    val isSubscriber: Boolean = false,
)

@Serializable
private class PaymentInfoDto(
    val isSubscriber: Boolean = false,
)
