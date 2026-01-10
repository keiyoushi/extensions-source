package eu.kanade.tachiyomi.extension.pt.spectralscan

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.random.Random

class NexusScan : HttpSource(), ConfigurableSource {

    // SpectralScan (pt-BR) -> Nexus Scan (pt-BR)
    override val id = 5304928452449566995L

    override val lang = "pt-BR"

    override val name = "Nexus Scan"

    override val baseUrl = "https://nexustoons.com"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val apiHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "application/json")
            .add("Referer", "$baseUrl/")
            .build()
    }

    private val onlyNsfw: Boolean
        get() = preferences.getBoolean(PREF_ONLY_NSFW_KEY, PREF_ONLY_NSFW_DEFAULT)

    // ==================== URL Helpers ==========================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.substringAfter("/read/").split("/")
        val chapterId = parts[0]
        val mangaSlug = parts.getOrNull(1) ?: ""
        return "$baseUrl/r/${encodeChapterUrl(chapterId, mangaSlug)}"
    }

    private fun encodeChapterUrl(chapterId: String, mangaSlug: String = ""): String {
        val timestamp = System.currentTimeMillis().toString(36)
        val padding = randomString(20 + Random.nextInt(11))
        val data = "$chapterId|$mangaSlug|$timestamp|$padding"

        val xored = xorCipher(data, CHAPTER_ENCRYPTION_KEY)
        val firstEncode = base64UrlEncode(xored)
        val secondEncode = base64UrlEncode("$firstEncode|${randomString(10)}")

        return if (secondEncode.length >= 64) {
            secondEncode
        } else {
            secondEncode + randomString(64 - secondEncode.length)
        }
    }

    private fun xorCipher(input: String, key: String): String {
        return input.mapIndexed { i, char ->
            (char.code xor key[i % key.length].code).toChar()
        }.joinToString("")
    }

    private fun base64UrlEncode(input: String): String {
        val bytes = input.map { it.code.toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(length) {
            repeat(length) { append(chars.random()) }
        }
    }

    // ==================== Popular ==========================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "50")
            .addQueryParameter("includeNsfw", "true")
            .apply { if (onlyNsfw) addQueryParameter("onlyNsfw", "true") }
            .addQueryParameter("sortBy", "views")
            .build()
        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.orEmpty().map { it.toSManga() }
        val hasNextPage = result.page < result.pages
        return MangasPage(mangas, hasNextPage)
    }

    // ==================== Latest ==========================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "50")
            .addQueryParameter("includeNsfw", "true")
            .apply { if (onlyNsfw) addQueryParameter("onlyNsfw", "true") }
            .addQueryParameter("sortBy", "lastChapterAt")
            .build()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ==================== Search ==========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "30")
            .addQueryParameter("includeNsfw", "true")

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        var sortBy = "updatedAt"
        var sortOrder = "desc"
        var categoryMode = "or"
        val statusList = mutableListOf<String>()
        val typeList = mutableListOf<String>()
        val genreList = mutableListOf<String>()
        val themeList = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> {
                    val value = filter.selected()
                    if (value.isNotEmpty()) {
                        when (filter.parameter) {
                            "sortBy" -> sortBy = value
                            "sortOrder" -> sortOrder = value
                            "categoryMode" -> categoryMode = value
                        }
                    }
                }
                is CheckboxGroup -> {
                    val selected = filter.selected()
                    if (selected.isNotEmpty()) {
                        when (filter.parameter) {
                            "status" -> statusList.addAll(selected)
                            "type" -> typeList.addAll(selected)
                            "genres" -> genreList.addAll(selected)
                            "themes" -> themeList.addAll(selected)
                        }
                    }
                }
                else -> {}
            }
        }

        url.addQueryParameter("sortBy", sortBy)
        url.addQueryParameter("sortOrder", sortOrder)
        url.addQueryParameter("categoryMode", categoryMode)

        if (statusList.isNotEmpty()) {
            url.addQueryParameter("status", statusList.joinToString(","))
        }
        if (typeList.isNotEmpty()) {
            url.addQueryParameter("type", typeList.joinToString(","))
        }
        if (genreList.isNotEmpty()) {
            url.addQueryParameter("genres", genreList.joinToString(","))
        }
        if (themeList.isNotEmpty()) {
            url.addQueryParameter("themes", themeList.joinToString(","))
        }

        return GET(url.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ==================== Details =======================

    private fun getMangaSlug(url: String) = url.substringAfter("/manga/").trimEnd('/')

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = getMangaSlug(manga.url)
        return GET("$baseUrl/api/manga/$slug", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaDetailsDto>().toSManga()
    }

    // ==================== Chapter =======================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = getMangaSlug(manga.url)
        return GET("$baseUrl/api/manga/$slug", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MangaDetailsDto>()
        return manga.chapters.orEmpty().map { it.toSChapter(manga.slug) }
    }

    // ==================== Page ==========================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/read/").substringBefore("/")
        return GET("$baseUrl/api/read/$chapterId", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val readResponse = response.parseAs<ReadResponse>()
        return readResponse.pages
            .sortedBy { it.pageNumber }
            .mapIndexed { index, page ->
                Page(index, imageUrl = page.imageUrl)
            }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    // ==================== Filters ==========================

    override fun getFilterList() = FilterList(
        SelectFilter("Ordenar Por", "sortBy", sortList),
        SelectFilter("Ordem", "sortOrder", orderList),
        SelectFilter("Modo de Categoria", "categoryMode", categoryModeList),
        CheckboxGroup(
            "Status",
            "status",
            statusList.map { CheckboxItem(it.first, it.second) },
        ),
        CheckboxGroup(
            "Tipo",
            "type",
            typeList.map { CheckboxItem(it.first, it.second) },
        ),
        CheckboxGroup(
            "Gêneros",
            "genres",
            genreList.map { CheckboxItem(it.first, it.second) },
        ),
        CheckboxGroup(
            "Temas",
            "themes",
            themeList.map { CheckboxItem(it.first, it.second) },
        ),
    )

    // ==================== Settings ==========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ONLY_NSFW_KEY
            title = "Mostrar apenas conteúdo +18"
            summary = "Quando habilitado, exibe apenas conteúdo adulto nas listagens."
            setDefaultValue(PREF_ONLY_NSFW_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_ONLY_NSFW_KEY = "pref_only_nsfw"
        private const val PREF_ONLY_NSFW_DEFAULT = false
        private const val CHAPTER_ENCRYPTION_KEY = "NexusToons2026SecretKeyForChapterEncryption!@#\$"
    }
}
