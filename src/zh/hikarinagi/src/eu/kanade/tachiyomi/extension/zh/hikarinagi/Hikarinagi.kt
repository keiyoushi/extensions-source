package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class Hikarinagi : KeiSource() {

    override fun getHomeUrl() = "$baseUrl/mangas"

    private companion object {
        // Nuxt 3 SSR payload 的 script 标签前缀
        const val NUXT_MARKER = """<script type="application/json" data-nuxt-data="nuxt-app""""
    }

    // override fun OkHttpClient.Builder.configureClient() = apply {
    //     rateLimit(4) { it.host == baseUrl.toHttpUrl().host }
    //     rateLimit(4) { it.host == "images.yurari.moe" }
    // }

    // ---------------- 列表 ----------------

    private fun browseUrl(page: Int, query: String?, filters: FilterList): HttpUrl {
        val url = "$baseUrl/api/pages/mangas/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "24")

        query?.takeIf { it.isNotBlank() }?.let {
            url.addQueryParameter("search", it)
        }

        filters.filterIsInstance<SortFilter>().firstOrNull()?.activeValue?.let {
            url.addQueryParameter("sort", it)
        }

        filters.filterIsInstance<GenreFilter>().firstOrNull()?.activeValue?.let {
            url.addQueryParameter("genre", it)
        }

        return url.build()
    }

    private fun parseBrowse(response: Response): MangasPage {
        val data = response.parseAs<BrowseResponse>()
        val manga = data.list.items.map { it.toSManga() }
        val hasNext = data.list.meta.page < data.list.meta.totalPages
        return MangasPage(manga, hasNext)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val filters = FilterList(SortFilter(Filter.Sort.Selection(1, false)))
        val response = client.get(browseUrl(page, null, filters))
        return parseBrowse(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = FilterList(SortFilter(Filter.Sort.Selection(0, false)))
        val response = client.get(browseUrl(page, null, filters))
        return parseBrowse(response)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(SortFilter(), GenreFilter())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.get(browseUrl(page, query, filters))
        return parseBrowse(response)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.lastOrNull()?.toLongOrNull() ?: return null
        return fetchMangaDetailsById(id)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val id = url.pathSegments.lastOrNull()?.toLongOrNull()
        if (id == null) {
            // /mangas/browse?search=...&genre=... 列表 URL
            val query = url.queryParameter("search") ?: url.queryParameter("keyword")
            val genre = url.queryParameter("genre")
            val genreIndex = GENRES.indexOfFirst { it.value == genre }.let { if (it >= 0) it else 0 }
            val filters = FilterList(
                SortFilter(Filter.Sort.Selection(0, false)),
                GenreFilter(genreIndex),
            )
            return parseBrowse(client.get(browseUrl(page, query, filters)))
        }
        val manga = fetchMangaDetailsById(id) ?: return MangasPage(emptyList(), false)
        return MangasPage(listOf(manga), false)
    }

    // ---------------- 详情 ----------------

    /**
     * 读取 Nuxt 3 SSR 页面的 __NUXT_DATA__ payload 原始数组。
     */
    private suspend fun fetchNuxtPayload(url: String): JsonArray = fetchNuxtPayloadRaw(client.get(url).use { it.body.string() }, "网页")

    private fun fetchNuxtPayloadRaw(html: String, what: String): JsonArray {
        val start = html.indexOf(NUXT_MARKER)
        if (start < 0) throw Exception("$what 中没有找到 Nuxt payload")

        val tagEnd = html.indexOf('>', start)
        if (tagEnd < 0) throw Exception("Nuxt script 标签不完整")

        val end = html.indexOf("</script>", tagEnd)
        if (end < 0) throw Exception("Nuxt payload 不完整")

        val raw = html.substring(tagEnd + 1, end)
        return jsonInstance.parseToJsonElement(raw) as? JsonArray
            ?: throw Exception("Nuxt payload 不是数组")
    }

    /**
     * 按需解析 Nuxt payload。
     *
     * payload 是数组：JSON int 和数字字符串是对数组其他位置的「引用」。
     * 与全量 deepResolve 不同，这里用 memo 缓存每个槽位的解析结果，
     * 只展开实际被引用的结构，避免共享子树被重复展开（这是 OOM 的根源）。
     * `["Type", ref]` 类型化引用取 ref 继续解析。
     */
    private class NuxtRefResolver(private val array: JsonArray) {
        private val cache = HashMap<Int, JsonElement>()

        fun resolve(elem: JsonElement?, depth: Int = 0): JsonElement? {
            if (depth > 12) return null
            if (elem == null) return null
            return when (elem) {
                // JSON int 或数字字符串 = 对数组其他位置的引用
                is JsonPrimitive -> {
                    val idx = elem.contentOrNull?.toIntOrNull()
                    if (idx != null && idx in array.indices) resolveIndex(idx, depth) else elem
                }
                is JsonObject -> {
                    val out = LinkedHashMap<String, JsonElement>()
                    for ((k, v) in elem) {
                        out[k] = resolve(v, depth + 1) ?: JsonNull
                    }
                    JsonObject(out)
                }
                is JsonArray -> {
                    val items = mutableListOf<JsonElement>()
                    for (v in elem) {
                        items += resolve(v, depth + 1) ?: JsonNull
                    }
                    JsonArray(items)
                }
            }
        }

        private fun resolveIndex(idx: Int, depth: Int): JsonElement {
            cache[idx]?.let { return it }
            val resolved = when (// 带 `$key` 的浅响应状态对象：不递归，返回空对象
                val target = array[idx]) {
                is JsonObject if target.keys.any { it.startsWith("$") } -> JsonObject(emptyMap())
                // `["Type", ref]` 类型化引用：取 ref 继续
                is JsonArray if target.size == 2 && target[0] is JsonPrimitive && (target[0] as JsonPrimitive).isString ->
                    resolve(target[1], depth + 1) ?: JsonNull

                else -> resolve(target, depth + 1) ?: JsonNull
            }
            cache[idx] = resolved
            return resolved
        }
    }

    /**
     * 从 payload 中定位详情容器：data[4] 是 fetch 根容器 { manga, chapters, ... }，
     * 值都是对数组的引用。返回解析后的 DetailPayload。
     */
    private fun parseDetailPayload(array: JsonArray): DetailPayload? {
        // 找 fetch 根容器：一个 JsonObject，其 key 集合含 manga/chapters
        var root: JsonObject? = null
        for (elem in array) {
            if (elem is JsonObject && elem["manga"] != null && elem["chapters"] != null) {
                root = elem
                break
            }
        }
        val rootObj = root ?: return null

        val resolver = NuxtRefResolver(array)
        fun field(name: String): JsonElement? {
            val ref = rootObj[name] ?: return null
            return resolver.resolve(ref)
        }

        val mangaJson = field("manga") as? JsonObject ?: return null

        val manga = try {
            jsonInstance.decodeFromJsonElement(MangaItem.serializer(), mangaJson)
        } catch (_: Exception) {
            return null
        }

        val chapters = (field("chapters") as? JsonArray)?.mapNotNull { ch ->
            try {
                jsonInstance.decodeFromJsonElement(ChapterItem.serializer(), ch)
            } catch (_: Exception) {
                null
            }
        }.orEmpty()

        val people = (field("people") as? JsonArray)?.mapNotNull { s ->
            try {
                jsonInstance.decodeFromJsonElement(Staff.serializer(), s)
            } catch (_: Exception) {
                null
            }
        }.orEmpty()

        val producers = (field("producers") as? JsonArray)?.mapNotNull { s ->
            try {
                jsonInstance.decodeFromJsonElement(Staff.serializer(), s)
            } catch (_: Exception) {
                null
            }
        }.orEmpty()

        val tags = (field("tags") as? JsonArray)?.mapNotNull { t ->
            try {
                jsonInstance.decodeFromJsonElement(TagItem.serializer(), t)
            } catch (_: Exception) {
                null
            }
        }.orEmpty()

        return DetailPayload(
            manga = manga,
            chapters = chapters,
            people = people,
            producers = producers,
            tags = tags,
        )
    }

    private suspend fun fetchMangaDetailsById(id: Long): SManga? {
        // 详情页 SSR payload 含 manga + chapters + people(staff) + producers + tags
        val array = fetchNuxtPayload("$baseUrl/mangas/$id")
        val detail = parseDetailPayload(array) ?: return null
        val mangaItem = detail.manga ?: return null

        return mangaItem.toSManga(
            includeSummary = true,
            detail = detail,
        )
    }

    private fun mangaIdOf(manga: SManga): Long = manga.url.substringAfterLast('/').substringBefore('?').toLongOrNull() ?: 0L

    private suspend fun fetchChapterListOf(manga: SManga): List<SChapter> {
        val id = mangaIdOf(manga)
        if (id == 0L) return emptyList()

        // 用详情页 SSR payload 拿章节（含完整元数据），一次请求
        val array = fetchNuxtPayload("$baseUrl/mangas/$id")
        val detail = parseDetailPayload(array)

        val chapters = detail?.chapters?.takeIf { it.isNotEmpty() } ?: run {
            // fallback：API
            val response = client.get("$baseUrl/api/v3/mangas/$id/chapters")
            response.parseAs<ChaptersResponse>().data
        }

        // 站点给的是升序（第1话在前），Mihon 默认倒序显示 → 反转
        return chapters.asReversed().map { it.toSChapter(id) }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast('/').substringBefore('?').toLongOrNull()
            ?: return SMangaUpdate(manga, chapters)

        // 无论 fetchDetails 是否开启，都尝试拉一次详情（幂等、一次请求）：
        // 列表项から进入的漫画 initialized=true 但 description/author/genre 为空，
        // 下拉刷新/同步时需要补全这些字段。
        val newManga = fetchMangaDetailsById(id) ?: manga
        val newChapters = if (fetchChapters) fetchChapterListOf(manga) else chapters

        return SMangaUpdate(newManga, newChapters)
    }

    // ---------------- 阅读 ----------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl${chapter.url}")
        val pages = parseReaderPages(response)

        return pages.map { page ->
            Page(
                index = page.pageNumber - 1,
                imageUrl = page.src,
            )
        }
    }

    private data class NuxtPage(
        val pageNumber: Int,
        val src: String,
    )

    /**
     * 解析 Nuxt 3 SSR 阅读页 payload 并解析引用，返回图片页列表。
     *
     * 对于需要登录/付费的章节，payload 会退化为 NuxtError（401 AUTH_UNAUTHENTICATED），
     * 此时抛出带明确提示的异常。
     */
    private fun parseReaderPages(response: Response): List<NuxtPage> {
        val html = response.use { it.body.string() }
        val raw = html.substringAfter(NUXT_MARKER).substringAfter('>').substringBefore("</script>")
        val array = fetchNuxtPayloadRaw(html, "阅读页")

        // 登录/权限检测：payload 内出现 "AUTH_UNAUTHENTICATED" 或 "请先登录" 即判定需要登录
        if (raw.contains("AUTH_UNAUTHENTICATED") || raw.contains("请先登录")) {
            throw Exception("请在 WebView 中登录后继续阅读")
        }

        // Nuxt 3 payload 编码规则：
        // - payload 是数组；JSON int 值 = 对数组其他位置的「引用」
        // - 引用指向标量（int/字符串）即为字面值；指向 dict/array 则递归
        // - 页面列表是一个「全部由 int 引用组成」的数组，引用指向 page dict

        // 1) 找到页引用数组：所有元素都是 int，且指向含 page_number/src 的 dict
        fun findPageRefArray(): JsonArray? {
            for (elem in array) {
                if (elem !is JsonArray || elem.isEmpty()) continue
                val refs = elem.mapNotNull { item ->
                    (item as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.takeIf { it in array.indices }
                }
                if (refs.size != elem.size) continue
                val allPages = refs.all { idx ->
                    val t = array[idx]
                    t is JsonObject && t["page_number"] != null && t["src"] != null
                }
                if (allPages) return elem
            }
            return null
        }

        // 2) 把引用解析为字面值：int 引用 → 指向目标；指向标量则返回标量本身（不再解引用）
        fun resolveRef(value: JsonElement?): JsonElement? {
            if (value == null) return null
            val p = value as? JsonPrimitive ?: return value
            if (p.isString) return value
            val idx = p.contentOrNull?.toIntOrNull() ?: return value
            if (idx !in array.indices) return value
            return array[idx]
        }

        val pageRefs = findPageRefArray()
            ?: throw Exception("Nuxt payload 中没有页引用数组")

        val pages = pageRefs.mapNotNull { refElem ->
            val p = refElem as? JsonPrimitive ?: return@mapNotNull null
            val refIdx = p.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val obj = array.getOrNull(refIdx) as? JsonObject ?: return@mapNotNull null

            val num = (resolveRef(obj["page_number"]) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            val src = (resolveRef(obj["src"]) as? JsonPrimitive)?.contentOrNull ?: ""
            if (src.isBlank()) null else NuxtPage(num, src)
        }

        if (pages.isEmpty()) throw Exception("阅读页中没有图片")

        return pages
    }

    // ---------------- 模型转换 ----------------

    private fun MangaItem.toSManga(
        includeSummary: Boolean = false,
        detail: DetailPayload? = null,
    ): SManga = SManga.create().apply {
        applyCommon(id, name, nameCn, serialStatus, covers)

        // 作者：people 中作画/原作/故事角色，去重后合并
        val authorNames = detail?.people.orEmpty().mapNotNull { s ->
            s.person?.name?.takeIf { it.isNotBlank() }
        }.distinct()
        author = authorNames.joinToString(", ")

        // 标签：全部 tag 名，用 joinToString 默认分隔符 ", " 拼接
        val tagNames = detail?.tags.orEmpty().mapNotNull { it.tag?.name?.takeIf { n -> n.isNotBlank() } }
        genre = tagNames.joinToString()

        if (includeSummary) {
            // 简介只放干净的 summary（优先中文），不要作者/标签装饰
            description = listOfNotNull(
                summaryCn?.takeIf { it.isNotBlank() },
                summary?.takeIf { it.isNotBlank() },
            ).joinToString("\n\n")
        }
        initialized = true
    }

    private fun BrowseItem.toSManga(): SManga = SManga.create().apply {
        applyCommon(id, name, nameCn, serialStatus, covers)
        initialized = true
    }

    private fun SManga.applyCommon(
        id: Long,
        name: String,
        nameCn: String?,
        serialStatus: String?,
        covers: List<MangaCover>,
    ) {
        // 只显示中文名（name_cn）；没有中文名才退回日文原名
        title = nameCn?.takeIf { it.isNotBlank() } ?: name
        url = "/mangas/$id"
        thumbnail_url = covers.firstOrNull()?.media?.src?.let { mediaSrc ->
            if (mediaSrc.startsWith("http")) mediaSrc else "https://images.yurari.moe/$mediaSrc"
        }
        status = when (serialStatus) {
            "SERIALIZING" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun ChapterItem.toSChapter(mangaId: Long): SChapter {
        val chapterLabel = chapterNumber?.takeIf { it.isNotBlank() }
        val titleLabel = name.takeIf { it.isNotBlank() }

        // 站点 name 本身就是 "第16話"，去掉与 chapterNumber 重复的前缀，只保留标题
        val chapterName = when {
            titleLabel != null && titleLabel != "第${chapterLabel}話" && titleLabel != "第${chapterLabel}话" ->
                titleLabel
            else -> "第 ${chapterLabel ?: "?"} 话"
        }

        return SChapter.create().apply {
            this.name = chapterName
            url = "/mangas/$mangaId/read/$id"
            date_upload = 0L
            chapter_number = chapterLabel?.toFloatOrNull() ?: -1f
            // 补充信息：页数显示在 scanlator 字段
            scanlator = pageCount?.let { "${it}P" }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"
}
