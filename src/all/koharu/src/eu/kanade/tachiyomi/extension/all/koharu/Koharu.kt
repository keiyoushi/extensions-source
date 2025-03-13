package eu.kanade.tachiyomi.extension.all.koharu

import android.content.SharedPreferences
import androidx.preference.ListPreference
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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Koharu(
    override val lang: String = "all",
    private val searchLang: String = "",
) : HttpSource(), ConfigurableSource {

    override val name = "SchaleNetwork"

    override val baseUrl = "https://schale.network"

    override val id = if (lang == "en") 1484902275639232927 else super.id

    private val apiUrl = baseUrl.replace("://", "://api.")

    private val apiBooksUrl = "$apiUrl/books"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    private val json: Json by injectLazy()

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = replace(shortenTitleRegex, "").trim()

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun quality() = preferences.getString(PREF_IMAGERES, "1280")!!

    private fun remadd() = preferences.getBoolean(PREF_REM_ADD, false)

    private fun getDomain(): String {
        try {
            val noRedirectClient = client.newBuilder().followRedirects(false).build()
            val host = noRedirectClient.newCall(GET(baseUrl, headers)).execute()
                .headers["Location"]?.toHttpUrlOrNull()?.host
                ?: return baseUrl
            return "https://$host"
        } catch (_: Exception) {
            return baseUrl
        }
    }

    private val lazyHeaders by lazy {
        val domain = getDomain()
        headersBuilder()
            .set("Referer", "$domain/")
            .set("Origin", domain)
            .build()
    }

    private fun getManga(book: Entry) = SManga.create().apply {
        setUrlWithoutDomain("${book.id}/${book.public_key}")
        title = if (remadd()) book.title.shortenTitle() else book.title
        thumbnail_url = book.thumbnail.path
    }

    private fun getImagesByMangaEntry(entry: MangaEntry): Pair<ImagesInfo, String> {
        val data = entry.data
        fun getIPK(
            ori: DataKey?,
            alt1: DataKey?,
            alt2: DataKey?,
            alt3: DataKey?,
            alt4: DataKey?,
        ): Pair<Int?, String?> {
            return Pair(
                ori?.id ?: alt1?.id ?: alt2?.id ?: alt3?.id ?: alt4?.id,
                ori?.public_key ?: alt1?.public_key ?: alt2?.public_key ?: alt3?.public_key ?: alt4?.public_key,
            )
        }
        val (id, public_key) = when (quality()) {
            "1600" -> getIPK(data.`1600`, data.`1280`, data.`0`, data.`980`, data.`780`)
            "1280" -> getIPK(data.`1280`, data.`1600`, data.`0`, data.`980`, data.`780`)
            "980" -> getIPK(data.`980`, data.`1280`, data.`0`, data.`1600`, data.`780`)
            "780" -> getIPK(data.`780`, data.`980`, data.`0`, data.`1280`, data.`1600`)
            else -> getIPK(data.`0`, data.`1600`, data.`1280`, data.`980`, data.`780`)
        }

        if (id == null || public_key == null) {
            throw Exception("No Images Found")
        }

        val realQuality = when (id) {
            data.`1600`?.id -> "1600"
            data.`1280`?.id -> "1280"
            data.`980`?.id -> "980"
            data.`780`?.id -> "780"
            else -> "0"
        }

        val imagesResponse = client.newCall(GET("$apiBooksUrl/data/${entry.id}/${entry.public_key}/$id/$public_key?v=${entry.updated_at ?: entry.created_at}&w=$realQuality", lazyHeaders)).execute()
        val images = imagesResponse.parseAs<ImagesInfo>() to realQuality
        return images
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$apiBooksUrl?page=$page" + if (searchLang.isNotBlank()) "&s=language!:\"$searchLang\"" else "", lazyHeaders)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$apiBooksUrl?sort=8&page=$page" + if (searchLang.isNotBlank()) "&s=language!:\"$searchLang\"" else "", lazyHeaders)
    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Books>()

        return MangasPage(data.entries.map(::getManga), data.page * data.limit < data.total)
    }

    // Search

    override fun getFilterList(): FilterList = getFilters()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_KEY_SEARCH) -> {
                val ipk = query.removePrefix(PREFIX_ID_KEY_SEARCH)
                val response = client.newCall(GET("$apiBooksUrl/detail/$ipk", lazyHeaders)).execute()
                Observable.just(searchMangaParse2(response))
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiBooksUrl.toHttpUrl().newBuilder().apply {
            val terms: MutableList<String> = mutableListOf()

            if (lang != "all") terms += "language!:\"$searchLang\""
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.getValue())

                    is CategoryFilter -> {
                        val activeFilter = filter.state.filter { it.state }
                        if (activeFilter.isNotEmpty()) {
                            addQueryParameter("cat", activeFilter.sumOf { it.value }.toString())
                        }
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            val tags = filter.state.split(",").filter(String::isNotBlank).joinToString(",")
                            if (tags.isNotBlank()) {
                                terms += "${filter.type}!:" + if (filter.type == "pages") tags else '"' + tags + '"'
                            }
                        }
                    }
                    else -> {}
                }
            }
            if (query.isNotEmpty()) terms.add("title:\"$query\"")
            if (terms.isNotEmpty()) addQueryParameter("s", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, lazyHeaders)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private fun searchMangaParse2(response: Response): MangasPage {
        val entry = response.parseAs<MangaEntry>()

        return MangasPage(
            listOf(
                SManga.create().apply {
                    setUrlWithoutDomain("${entry.id}/${entry.public_key}")
                    title = if (remadd()) entry.title.shortenTitle() else entry.title
                    thumbnail_url = entry.thumbnails.base + entry.thumbnails.main.path
                },
            ),
            false,
        )
    }
    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiBooksUrl/detail/${manga.url}", lazyHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaEntry>().toSManga()
    }

    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private fun MangaEntry.toSManga() = SManga.create().apply {
        val artists = mutableListOf<String>()
        val circles = mutableListOf<String>()
        val parodies = mutableListOf<String>()
        val magazines = mutableListOf<String>()
        val characters = mutableListOf<String>()
        val cosplayers = mutableListOf<String>()
        val females = mutableListOf<String>()
        val males = mutableListOf<String>()
        val mixed = mutableListOf<String>()
        val other = mutableListOf<String>()
        val uploaders = mutableListOf<String>()
        val tags = mutableListOf<String>()
        for (tag in this@toSManga.tags) {
            when (tag.namespace) {
                1 -> artists.add(tag.name)
                2 -> circles.add(tag.name)
                3 -> parodies.add(tag.name)
                4 -> magazines.add(tag.name)
                5 -> characters.add(tag.name)
                6 -> cosplayers.add(tag.name)
                7 -> tag.name.takeIf { it != "anonymous" }?.let { uploaders.add(it) }
                8 -> males.add(tag.name + " ♂")
                9 -> females.add(tag.name + " ♀")
                10 -> mixed.add(tag.name)
                12 -> other.add(tag.name)
                else -> tags.add(tag.name)
            }
        }

        var appended = false
        fun List<String>.joinAndCapitalizeEach(): String? = this.emptyToNull()?.joinToString { it.capitalizeEach() }?.apply { appended = true }
        title = if (remadd()) this@toSManga.title.shortenTitle() else this@toSManga.title

        author = (circles.emptyToNull() ?: artists).joinToString { it.capitalizeEach() }
        artist = artists.joinToString { it.capitalizeEach() }
        genre = (tags + males + females + mixed + other).joinToString { it.capitalizeEach() }
        description = buildString {
            circles.joinAndCapitalizeEach()?.let {
                append("Circles: ", it, "\n")
            }
            uploaders.joinAndCapitalizeEach()?.let {
                append("Uploaders: ", it, "\n")
            }
            magazines.joinAndCapitalizeEach()?.let {
                append("Magazines: ", it, "\n")
            }
            cosplayers.joinAndCapitalizeEach()?.let {
                append("Cosplayers: ", it, "\n")
            }
            parodies.joinAndCapitalizeEach()?.let {
                append("Parodies: ", it, "\n")
            }
            characters.joinAndCapitalizeEach()?.let {
                append("Characters: ", it, "\n")
            }

            if (appended) append("\n")

            try {
                append("Posted: ", dateReformat.format(created_at), "\n")
            } catch (_: Exception) {}

            val dataKey = when (quality()) {
                "1600" -> data.`1600` ?: data.`1280` ?: data.`0`
                "1280" -> data.`1280` ?: data.`1600` ?: data.`0`
                "980" -> data.`980` ?: data.`1280` ?: data.`0`
                "780" -> data.`780` ?: data.`980` ?: data.`0`
                else -> data.`0`
            }
            append("Size: ", dataKey.readableSize(), "\n\n")
            append("Pages: ", thumbnails.entries.size, "\n\n")
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
        s.replaceFirstChar { sr ->
            if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
        }
    }

    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g/${manga.url}"

    // Chapter

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiBooksUrl/detail/${manga.url}", lazyHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<MangaEntry>()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = "${manga.id}/${manga.public_key}"
                date_upload = (manga.updated_at ?: manga.created_at)
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${chapter.url}"

    // Page List

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiBooksUrl/detail/${chapter.url}", lazyHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val mangaEntry = response.parseAs<MangaEntry>()
        val imagesInfo = getImagesByMangaEntry(mangaEntry)

        return imagesInfo.first.entries.mapIndexed { index, image ->
            Page(index, imageUrl = "${imagesInfo.first.base}/${image.path}?w=${imagesInfo.second}")
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, lazyHeaders)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Settings

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGERES
            title = "Image Resolution"
            entries = arrayOf("780x", "980x", "1280x", "1600x", "Original")
            entryValues = arrayOf("780", "980", "1280", "1600", "0")
            summary = "%s"
            setDefaultValue("1280")
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_REM_ADD
            title = "Remove additional information in title"
            summary = "Remove anything in brackets from manga titles.\n" +
                "Reload manga to apply changes to loaded manga."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    companion object {
        const val PREFIX_ID_KEY_SEARCH = "id:"
        private const val PREF_IMAGERES = "pref_image_quality"
        private const val PREF_REM_ADD = "pref_remove_additional"
    }
}
