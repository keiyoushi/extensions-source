package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Opens a volume from the store, or through decode.php, which hands out its file and the pass the file host asks for. */
internal class VolumeOpener(
    private val store: VolumeStore,
    private val decoder: VolumeDecoder,
    private val fileHost: FileHost,
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
                fileHost.checkNotPaused()
                val file = decoder.decode(titleUrl, label)
                store.download(address, downloads) { fileHost.download(file, it) }.also { it.awaitStarted() }
            }
        }
        return volume.pageUrls()
    }
}
