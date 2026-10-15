package eu.kanade.tachiyomi.extension.pt.spectralscan
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@Source
abstract class NexusToons :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(NexusDecrypt.createInterceptor())
        rateLimit(3, 1.seconds)
    }

    private val apiHeaders get() =
        headersBuilder()
            .set("Accept", "application/json")
            .build()

    // ==================== Popular ==========================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(
        page,
        "",
        FilterList(
            SelectFilter("", "sortBy", sortList),
        ),
    )

    // ==================== Latest ==========================

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    // ==================== Search ==========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 2) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "50")
            .addQueryParameter("includeNsfw", noNsfw.not().toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        var sortBy = "lastChapterAt"
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

        if (onlyNsfw && !noNsfw) url.addQueryParameter("onlyNsfw", "true")

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

        return parseSearch(
            client.get(url.build(), apiHeaders),
        )
    }

    private fun parseSearch(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.orEmpty().map { it.toSManga() }
        val hasNextPage = result.page < result.pages
        return MangasPage(mangas, hasNextPage)
    }

    // ==================== Details =======================

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = getMangaSlug(manga.url)
        val response = client.get("$baseUrl/api/manga/$slug", apiHeaders)
        val mangaDto = response.parseAs<MangaDetailsDto>()
        val chapterList = mangaDto.chapters.orEmpty().map {
            it.toSChapter(mangaDto.slug)
        }

        return SMangaUpdate(
            mangaDto.toSManga(),
            chapterList,
        )
    }

    // ==================== Page ==========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfter("/read/").substringBefore("/")
        val response = client.get("$baseUrl/api/read/$chapterId", apiHeaders)

        val readResponse = response.parseAs<ReadResponse>()
        val pages = readResponse.pages
        if (pages.first().imageUrl != null) {
            return pages.mapIndexed { index, page ->
                Page(
                    index,
                    imageUrl = page.imageUrl!!,
                )
            }
        }
        return pages.mapIndexed { index, _ ->
            Page(
                index,
                imageUrl = "$baseUrl/api/p/${readResponse.pageToken}/$index",
            )
        }
    }

    // ==================== Filters ==========================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/api/categories").parseAs<List<GenreDto>>().toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
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
        ) + data?.parseAs<List<GenreDto>>()?.let { filters ->
            listOfNotNull(
                filters.filter { it.type == "genre" }.takeIf { it.isNotEmpty() }?.let { genres ->
                    CheckboxGroup("Gêneros", "genres", genres.map { CheckboxItem(it.name, it.id.toString()) })
                },
                filters.filter { it.type == "theme" }.takeIf { it.isNotEmpty() }?.let { themes ->
                    CheckboxGroup("Temas", "themes", themes.map { CheckboxItem(it.name, it.id.toString()) })
                },
            )
        }.orEmpty(),
    )

    // ================== Preferences ========================

    private val preferences = getPreferences()

    private val onlyNsfw: Boolean
        get() = preferences.getBoolean(PREF_ONLY_NSFW_KEY, PREF_ONLY_NSFW_DEFAULT)

    private val noNsfw: Boolean
        get() = preferences.getBoolean(PREF_NO_NSFW_KEY, PREF_NO_NSFW_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val onlyNsfw = SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ONLY_NSFW_KEY
            title = "Mostrar apenas conteúdo +18"
            summary = "Quando habilitado, exibe apenas conteúdo adulto nas listagens."
            setDefaultValue(PREF_ONLY_NSFW_DEFAULT)
            setEnabled(!noNsfw)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_NO_NSFW_KEY
            title = "Ocultar adulto (+18)"
            summary = "Quando habilitado, oculta conteúdo adulto nas listagens."
            setDefaultValue(PREF_NO_NSFW_DEFAULT)
            setOnPreferenceChangeListener { _, value ->
                onlyNsfw.setEnabled(!(value as Boolean))
                true
            }
        }.also(screen::addPreference)
    }

    // ==================== URL Helpers ==========================

    private fun getMangaSlug(url: String) = url.substringAfter("/manga/").trimEnd('/')

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

    private fun xorCipher(input: String, key: String): String = input.mapIndexed { i, char ->
        (char.code xor key[i % key.length].code).toChar()
    }.joinToString("")

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

    companion object {
        private const val PREF_ONLY_NSFW_KEY = "pref_only_nsfw"
        private const val PREF_ONLY_NSFW_DEFAULT = false
        private const val PREF_NO_NSFW_KEY = "pref_no_nsfw"
        private const val PREF_NO_NSFW_DEFAULT = false
        private const val CHAPTER_ENCRYPTION_KEY = "NexusToons2026SecretKeyForChapterEncryption!@#\$"
    }
}
