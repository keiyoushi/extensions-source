package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import keiyoushi.network.Fetch
import keiyoushi.network.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.OutputStream

/** Opens a volume from the store, or through decode.php, which hands out its file and the pass the file host asks for. */
internal class VolumeOpener(
    private val store: VolumeStore,
    private val decoder: VolumeDecoder,
    private val client: OkHttpClient,
    private val headers: Headers,
    private val siteUrl: String,
) {
    private val downloads = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // decode.php's latest pass is the only one the file host takes, so the next volume is decoded once this one is
    // coming in
    private val decodeLock = Mutex()

    suspend fun open(titleUrl: String, label: String): List<String> {
        // Stands for the volume in the store; never requested from the site
        val address = titleUrl.toHttpUrl().newBuilder().addPathSegment("volume").addPathSegment(label).build()
        val volume = store.find(address) ?: decodeLock.withLock {
            // Another open of the same volume may have decoded it while this one waited
            store.find(address) ?: run {
                val file = decoder.decode(titleUrl, label)
                store.download(address, downloads) { download(file, it) }.also { it.awaitStarted() }
            }
        }
        return volume.pageUrls()
    }

    /** In one request, the way the site's PDF viewer fetches it. */
    private suspend fun download(file: HttpUrl, out: OutputStream) {
        // A volume is far too big for the HTTP cache, and is stored anyway
        client.get(file, headers, Fetch.XHR.from(siteUrl), NO_STORE, ensureSuccess = false).use { response ->
            when (response.code) {
                200 -> withContext(Dispatchers.IO) { response.body.byteStream().use { it.copyTo(out) } }
                // The host also answers a file that isn't there with 403, only without Cloudflare's block page
                403 -> throw if (response.body.string().contains("cloudflare", ignoreCase = true)) FileHostRefused() else VolumeMissing()
                429 -> throw IOException("The file host is limiting downloads after too many in a short time, try again later")
                else -> throw IOException("Volume download failed (HTTP ${response.code})")
            }
        }
    }

    private companion object {
        val NO_STORE: CacheControl = CacheControl.Builder().noStore().build()
    }
}

internal class VolumeMissing : IOException("Volume not found")

/** The host only lets a volume's file through with the pass decode.php hands out along with it. */
internal class FileHostRefused : IOException("The file host turned the volume away, try again in a few minutes")
