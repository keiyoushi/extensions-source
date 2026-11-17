package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Covers, fetched in batches by a WebView from a blank page on the site's origin and kept on disk. */
internal class Covers(
    private val baseUrl: String,
    private val dir: File,
    private val scope: CoroutineScope,
    private val userAgent: () -> String,
) {
    class Cover(val file: File, val type: String)

    private val pending = LinkedHashMap<String, CompletableDeferred<Cover>>()
    private var batching = false

    private fun fileFor(url: String) = File(dir, "${url.hashCode().toUInt()}-${url.toHttpUrl().pathSegments.last()}")

    // The image type the site sent, kept next to the cover
    private fun typeFile(file: File) = File(file.path + ".type")

    /** The cover at [url], from disk or from the next batch. */
    fun get(url: String): CompletableDeferred<Cover> {
        val file = fileFor(url)
        if (file.exists() && typeFile(file).exists()) return CompletableDeferred(Cover(file, typeFile(file).readText()))
        synchronized(this) {
            pending[url]?.let { return it }
            val deferred = CompletableDeferred<Cover>()
            pending[url] = deferred
            if (!batching) {
                batching = true
                scope.launch { runBatches() }
            }
            return deferred
        }
    }

    private suspend fun runBatches() {
        while (true) {
            // Let the app ask for the rest of a screen's covers first
            delay(BATCH_DELAY)
            val batch = synchronized(this) {
                if (pending.isEmpty()) {
                    batching = false
                    return
                }
                pending.entries.take(BATCH_SIZE).associate { it.key to it.value }.also { pending.keys.removeAll(it.keys) }
            }
            try {
                fetch(batch)
            } catch (e: Throwable) {
                val error = e as? IOException ?: IOException(e.message, e)
                batch.values.forEach { it.completeExceptionally(error) }
            }
        }
    }

    private suspend fun fetch(batch: Map<String, CompletableDeferred<Cover>>) {
        dir.mkdirs()
        val bridge = "k" + ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val script = "(${javaClass.getResource("/assets/covers.js")!!.readText()})(\"$bridge\", ${batch.keys.toList().toJsonString()})"
        val origin = baseUrl.toHttpUrl().let { "${it.scheme}://${it.host}" }
        runWebView<Unit>(timeout = BATCH_TIMEOUT) {
            userAgent = this@Covers.userAgent()
            var left = batch.size
            webMessageBridge(bridge, setOf(origin)) { message ->
                val cover = message.parseAs<CoverMessage>()
                val deferred = batch[cover.url] ?: return@webMessageBridge
                if (cover.data != null && cover.type != null) {
                    val file = fileFor(cover.url)
                    file.writeBytes(Base64.decode(cover.data, Base64.DEFAULT))
                    typeFile(file).writeText(cover.type)
                    deferred.complete(Cover(file, cover.type))
                } else {
                    deferred.completeExceptionally(IOException(cover.error))
                }
                if (--left == 0) resolve(Unit)
            }
            onPageFinished { evaluateJs(script) }
            loadData(baseUrl, "<!DOCTYPE html><html><head></head><body></body></html>")
        }
    }

    @Serializable
    private class CoverMessage(val url: String, val type: String? = null, val data: String? = null, val error: String? = null)
}

private val BATCH_DELAY = 300.milliseconds
private const val BATCH_SIZE = 40
private val BATCH_TIMEOUT = 30.seconds
