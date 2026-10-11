package eu.kanade.tachiyomi.multisrc.pam

import keiyoushi.network.get
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * The site's reader, as the chunks the reader page loads. Each build renames the chunks and the
 * signer's exports, and sites rebuild every few days, so all of it is read from the site's own bundle.
 */
internal class ReaderModule(
    /** The chunk exporting the reader's attestation. */
    val sharedUrl: String,
    /** The signer's emscripten glue, which instantiates its WASM. */
    val glueUrl: String,
    /** Reader-side function name (`signManifest`, `kdfRot`, ...) to export name. */
    val exports: Map<String, String>,
)

internal suspend fun OkHttpClient.fetchReaderModule(baseUrl: String, headers: Headers): ReaderModule {
    fun assetUrl(name: String) = "$baseUrl/build/assets/$name"
    suspend fun asset(name: String): String = get(assetUrl(name), headers).use { it.body.string() }

    val home = get(baseUrl, headers).use { it.body.string() }
    val entry = ENTRY_REGEX.find(home)?.groupValues?.get(1) ?: throw IOException("Reader entry script not found")
    val entryScript = asset(entry)
    val reader = READER_CHUNK_REGEX.findAll(entryScript).lastOrNull()?.groupValues
        ?: throw IOException("Reader chunk not found")

    // Vite lists every chunk a page loads, transitively, next to the page's loader.
    val entryDeps = MAP_DEPS_REGEX.find(entryScript)?.groupValues?.get(1)
        ?.let { deps -> DEP_REGEX.findAll(deps).map { it.groupValues[1] }.toList() }
        .orEmpty()
    val readerDeps = reader[2].split(',').filter(String::isNotEmpty).mapNotNull { entryDeps.getOrNull(it.toInt()) }

    // The export name map lives in a chunk shared by every reader version.
    val (sharedName, shared) = (IMPORT_REGEX.findAll(asset(reader[1])).map { it.groupValues[1] } + readerDeps)
        .filter { it.endsWith(".js") && it != entry }
        .distinct()
        .toList()
        .firstNotNullOfOrNull { name -> asset(name).takeIf(FREE_BUFFER_REGEX::containsMatchIn)?.let { name to it } }
        ?: throw IOException("Reader signer bindings not found")
    val exports = EXPORT_MAP_REGEX.findAll(shared).map { it.value }.firstOrNull(FREE_BUFFER_REGEX::containsMatchIn)
        ?.let { map -> PAIR_REGEX.findAll(map).associate { it.groupValues[1] to it.groupValues[2] } }
        ?: throw IOException("Reader export map not found")

    val glueName = MAP_DEPS_REGEX.find(shared)?.groupValues?.get(1)
        ?.let { deps -> DEP_REGEX.findAll(deps).map { it.groupValues[1] }.toList() }
        .orEmpty()
        .filter { it.endsWith(".js") }
        .firstOrNull { name -> WASM_PREFIX in asset(name) }
        ?: throw IOException("Reader signer module not found")

    return ReaderModule(assetUrl(sharedName), assetUrl(glueName), exports)
}

private const val WASM_PREFIX = "\"AGFzbQ"

private val ENTRY_REGEX = Regex("""<script[^>]+src="(?:https?://[^/"]+)?/build/assets/([^"]+\.js)"""")
private val READER_CHUNK_REGEX = Regex(
    """"\./pages/(?:[\w-]+/)*serie-chapter-reader\.tsx":\(\)=>[\w$]+\(\(\)=>import\("\./([^"]+\.js)"\)(?:\.then\([^)]*\))?(?:,__vite__mapDeps\(\[([\d,]*)\]\))?""",
)
private val IMPORT_REGEX = Regex("""from"\./([^"]+\.js)"""")

// Builds ship the export map as `{freeBuffer:"_x",...}` or as `[["freeBuffer","_x"],...]`.
private val FREE_BUFFER_REGEX = Regex("""freeBuffer"?[:,]"_""")
private val EXPORT_MAP_REGEX = Regex("""[{\[](?:\[?"?[\w$]+"?[:,]"_[\w$]+"]?,?)+[}\]]""")
private val PAIR_REGEX = Regex(""""?([\w$]+)"?[:,]"(_[\w$]+)"""")
private val MAP_DEPS_REGEX = Regex("""m\.f=\[([^\]]+)\]""")
private val DEP_REGEX = Regex(""""assets/([^"]+)"""")
