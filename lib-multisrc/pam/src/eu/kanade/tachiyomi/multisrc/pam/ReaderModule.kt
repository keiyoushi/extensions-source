package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * The reader's signer as shipped by the site. Each build renames the WASM exports and reshuffles
 * the tables of the `a.b` import that unmasks the signer's secret, and sites rebuild every few
 * days, so all of it is read from the site's own bundle instead of being shipped with the
 * extension.
 */
internal class ReaderModule(
    val module: WasmModule,
    /** Reader-side function name (`signAttestation`, `malloc`, ...) to export name. */
    val exports: Map<String, String>,
    /** The only host functions the signer may import, as module and name. */
    val resizeImport: Pair<String, String>,
    val unmaskImport: Pair<String, String>,
    val unmaskPermutation: IntArray,
    val unmaskXor: IntArray,
    val unmaskAdd: IntArray,
) {
    fun export(name: String): String = exports[name] ?: throw IOException("Reader export $name missing")
}

internal fun OkHttpClient.fetchReaderModule(baseUrl: String, headers: Headers): ReaderModule {
    fun asset(name: String): String = newCall(GET("$baseUrl/build/assets/$name", headers)).execute().use {
        if (!it.isSuccessful) throw IOException("HTTP ${it.code} for reader asset $name")
        it.body.string()
    }

    val home = newCall(GET(baseUrl, headers)).execute().use { it.body.string() }
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
    val shared = (IMPORT_REGEX.findAll(asset(reader[1])).map { it.groupValues[1] } + readerDeps)
        .filter { it.endsWith(".js") && it != entry }
        .distinct()
        .map(::asset)
        .firstOrNull { "freeBuffer:\"" in it }
        ?: throw IOException("Reader signer bindings not found")
    val semantic = EXPORT_MAP_REGEX.find(shared)?.value
        ?.let { map -> PAIR_REGEX.findAll(map).associate { it.groupValues[1] to it.groupValues[2] } }
        ?: throw IOException("Reader export map not found")

    val glue = MAP_DEPS_REGEX.find(shared)?.groupValues?.get(1)
        ?.let { deps -> DEP_REGEX.findAll(deps).map { it.groupValues[1] } }
        .orEmpty()
        .filter { it.endsWith(".js") }
        .map(::asset)
        .firstOrNull { WASM_PREFIX in it }
        ?: throw IOException("Reader signer module not found")

    val glueNames = GLUE_EXPORT_REGEX.findAll(glue).associate { it.groupValues[1] to it.groupValues[2] }
    val exports = buildMap {
        semantic.forEach { (name, glueName) -> glueNames[glueName]?.let { put(name, it) } }
        glueNames["_malloc"]?.let { put("malloc", it) }
        CTORS_REGEX.find(glue)?.groupValues?.get(1)?.let { put("ctors", it) }
    }

    val unmask = UNMASK_REGEX.find(glue)?.groupValues ?: throw IOException("Unsupported reader signer build")
    val tables = unmask.slice(3..5)
        .map { group -> group.split(',').map(String::toInt).toIntArray() }
        .takeIf { tables -> tables.all { it.size == UNMASK_SIZE } }
        ?: throw IOException("Unsupported reader signer build")
    val (importObject, resizeName) = RESIZE_IMPORT_REGEX.find(glue)?.destructured
        ?: throw IOException("Unsupported reader signer build")
    val importModule = Regex("""var [\w$]+=\{([\w$]+):${Regex.escape(importObject)}\}""").find(glue)?.groupValues?.get(1)
        ?: throw IOException("Unsupported reader signer build")

    val wasm = WASM_REGEX.find(glue)?.groupValues?.get(1) ?: throw IOException("Reader signer module not found")

    return ReaderModule(
        module = Parser.parse(Base64.decode(wasm, Base64.DEFAULT)),
        exports = exports,
        resizeImport = importModule to resizeName,
        unmaskImport = importModule to unmask[1],
        unmaskPermutation = tables[0],
        unmaskXor = tables[1],
        unmaskAdd = tables[2],
    )
}

internal const val UNMASK_SIZE = 64
private const val WASM_PREFIX = "\"AGFzbQ"

private val ENTRY_REGEX = Regex("""<script[^>]+src="(?:https?://[^/"]+)?/build/assets/([^"]+\.js)"""")
private val READER_CHUNK_REGEX = Regex(
    """"\./pages/(?:[\w-]+/)*serie-chapter-reader\.tsx":\(\)=>[\w$]+\(\(\)=>import\("\./([^"]+\.js)"\)(?:\.then\([^)]*\))?(?:,__vite__mapDeps\(\[([\d,]*)\]\))?""",
)
private val IMPORT_REGEX = Regex("""from"\./([^"]+\.js)"""")
private val EXPORT_MAP_REGEX = Regex("""\{freeBuffer:"[^}]+\}""")
private val PAIR_REGEX = Regex("""([\w$]+):"(_[\w$]+)"""")
private val MAP_DEPS_REGEX = Regex("""m\.f=\[([^\]]+)\]""")
private val DEP_REGEX = Regex(""""assets/([^"]+)"""")
private val GLUE_EXPORT_REGEX = Regex("""[\w$]+\.(_[\w$]+)=[\w$]+\.([\w$]+)""")
private val CTORS_REGEX = Regex("""=!0,[\w$]+\.([\w$]+)\(\),null==""")

// emscripten_resize_heap, the first entry of the glue's import object.
private val RESIZE_IMPORT_REGEX = Regex(
    """([\w$]+)=\{([\w$]+):[\w$]+=>\{var [\w$]+=[\w$]+\.length;if\(\d+<\([\w$]+>>>=0\)\)return!1""",
)

// The import rewrites a 64-byte block in place: out[i] = (in[perm[i]] ^ xor[i]) + add[i].
private val UNMASK_REGEX = Regex(
    """([\w$]+):function\(([\w$]+)\)\{for\(var [\w$]+=\[([\d,]+)\],[\w$]+=\[([\d,]+)\],[\w$]+=\[([\d,]+)\],[\w$]+=[\w$]+\.slice\(\2,\2\+64\),[\w$]+=0;64>[\w$]+;[\w$]+\+\+\)[\w$]+\[\2\+([\w$]+)\]=\([\w$]+\[[\w$]+\[\6\]\]\^[\w$]+\[\6\]\)\+[\w$]+\[\6\]&255\}""",
)
private val WASM_REGEX = Regex(""""(AGFzbQ[A-Za-z0-9+/=]+)"""")
