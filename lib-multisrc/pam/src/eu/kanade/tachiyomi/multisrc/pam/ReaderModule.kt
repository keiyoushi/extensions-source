package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import keiyoushi.network.get
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * The reader's signer as shipped by the site. Each build renames the WASM exports and reshuffles
 * the tables of the imports that unmask the signer's secret, and sites rebuild every few days,
 * so all of it is read from the site's own bundle instead of being shipped with the extension.
 */
internal class ReaderModule(
    val module: WasmModule,
    /** Reader-side function name (`signAttestation`, `malloc`, ...) to export name. */
    val exports: Map<String, String>,
    /** The only host functions the signer may import, as module and name. */
    val resizeImport: Pair<String, String>,
    val unmaskImports: Map<Pair<String, String>, Unmask>,
) {
    fun export(name: String): String = exports[name] ?: throw IOException("Reader export $name missing")
}

/**
 * Rewrites a 64-byte block in place. Each step picks a byte through [permutation] and folds a
 * table-driven value into a running byte; the tables, the fold and the loop shape are reshuffled
 * per build, so all of it is read from the site's glue.
 */
internal class Unmask(
    val permutation: IntArray,
    val xor: IntArray,
    val add: IntArray,
    val rotate: IntArray,
    val mode: IntArray,
    val seed: Int,
    val passes: Int,
    val ascending: Boolean,
    /** Rotate-left amount folded into each step; `-1` folds with XOR instead. */
    val foldRotate: Int,
)

internal suspend fun OkHttpClient.fetchReaderModule(baseUrl: String, headers: Headers): ReaderModule {
    suspend fun asset(name: String): String = get("$baseUrl/build/assets/$name", headers).use { it.body.string() }

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
    val shared = (IMPORT_REGEX.findAll(asset(reader[1])).map { it.groupValues[1] } + readerDeps)
        .filter { it.endsWith(".js") && it != entry }
        .distinct()
        .toList()
        .firstNotNullOfOrNull { name -> asset(name).takeIf { "freeBuffer:\"" in it } }
        ?: throw IOException("Reader signer bindings not found")
    val semantic = EXPORT_MAP_REGEX.find(shared)?.value
        ?.let { map -> PAIR_REGEX.findAll(map).associate { it.groupValues[1] to it.groupValues[2] } }
        ?: throw IOException("Reader export map not found")

    val glue = MAP_DEPS_REGEX.find(shared)?.groupValues?.get(1)
        ?.let { deps -> DEP_REGEX.findAll(deps).map { it.groupValues[1] }.toList() }
        .orEmpty()
        .filter { it.endsWith(".js") }
        .firstNotNullOfOrNull { name -> asset(name).takeIf { WASM_PREFIX in it } }
        ?: throw IOException("Reader signer module not found")

    val glueNames = GLUE_EXPORT_REGEX.findAll(glue).associate { it.groupValues[1] to it.groupValues[2] }
    val exports = buildMap {
        semantic.forEach { (name, glueName) -> glueNames[glueName]?.let { put(name, it) } }
        glueNames["_malloc"]?.let { put("malloc", it) }
        CTORS_REGEX.find(glue)?.groupValues?.get(1)?.let { put("ctors", it) }
    }

    val (importObject, resizeName) = RESIZE_IMPORT_REGEX.find(glue)?.destructured
        ?: throw IOException("Unsupported reader signer build")
    val importModule = Regex("""var [\w$]+=\{([\w$]+):${Regex.escape(importObject)}\}""").find(glue)?.groupValues?.get(1)
        ?: throw IOException("Unsupported reader signer build")

    // Builds ship one or more unmask imports, each with its own tables, loop shape and fold.
    val unmasks = UNMASK_REGEX.findAll(glue).associate { match ->
        val groups = match.groupValues
        val tables = (3..11 step 2)
            .associate { groups[it] to groups[it + 1] }
            .mapValues { (_, values) -> values.split(',').map(String::toInt).toIntArray() }
        if (tables.values.any { it.size != UNMASK_SIZE }) throw IOException("Unsupported reader signer build")

        val expression = groups[27]
        fun table(name: String?): IntArray = name?.let(tables::get) ?: throw IOException("Unsupported reader signer build")
        val roles = UNMASK_ROLE_REGEX.findAll(expression).associate { it.groupValues[1] to it.groupValues[2] }

        (importModule to groups[1]) to Unmask(
            permutation = table(groups[25]),
            xor = table(roles["^"]),
            add = table(roles["+"]),
            rotate = table(roles["<<"]),
            mode = table(roles["=="]),
            seed = groups[18].toInt(),
            passes = groups[13].toInt(),
            ascending = groups[23] == "++",
            foldRotate = if ("^=" in expression) -1 else UNMASK_FOLD_REGEX.find(expression)?.groupValues?.get(1)?.toInt() ?: 0,
        )
    }
    if (unmasks.isEmpty()) throw IOException("Unsupported reader signer build")

    val wasm = WASM_REGEX.find(glue)?.groupValues?.get(1) ?: throw IOException("Reader signer module not found")

    return ReaderModule(
        module = Parser.parse(Base64.decode(wasm, Base64.DEFAULT)),
        exports = exports,
        resizeImport = importModule to resizeName,
        unmaskImports = unmasks,
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

// name:function(p){for(var a=[..],b=[..],c=[..],d=[..],e=[..],f=0;n>f;f++)for(var
//   g=mem.slice(p,p+64),h=seed,i=start;cond;i++){var j=g[a[i]];h<fold>,mem[p+i]=h
private val UNMASK_REGEX = Regex(
    """([\w$]+):function\(([\w$]+)\)\{for\(var ([\w$]+)=\[([\d,]+)\],([\w$]+)=\[([\d,]+)\],([\w$]+)=\[([\d,]+)\],([\w$]+)=\[([\d,]+)\],([\w$]+)=\[([\d,]+)\],[\w$]+=0;(\d+)>[\w$]+;[\w$]+\+\+\)for\(var ([\w$]+)=[\w$]+\.slice\(([\w$]+),([\w$]+)\+64\),([\w$]+)=(\d+),([\w$]+)=(\d+);([^;]+);([\w$]+)(\+\+|--)\)\{var [\w$]+=([\w$]+)\[([\w$]+)\[([\w$]+)\]\];([^,]*)""",
)

// Which of the function's five tables each part of the step expression reads.
private val UNMASK_ROLE_REGEX = Regex("""(\^|\+|<<|==)([\w$]+)\[""")

// `(255&(o<<6|o>>2))`: the running byte rotated before it is added back.
private val UNMASK_FOLD_REGEX = Regex("""\(255&\([\w$]+<<(\d+)\|""")
private val WASM_REGEX = Regex(""""(AGFzbQ[A-Za-z0-9+/=]+)"""")
