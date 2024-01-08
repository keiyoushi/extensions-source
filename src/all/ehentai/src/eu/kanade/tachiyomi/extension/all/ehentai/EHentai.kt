package eu.kanade.tachiyomi.extension.all.ehentai

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.CheckBox
import eu.kanade.tachiyomi.source.model.Filter.Group
import eu.kanade.tachiyomi.source.model.Filter.Select
import eu.kanade.tachiyomi.source.model.Filter.Text
import eu.kanade.tachiyomi.source.model.Filter.TriState
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

abstract class EHentai(
    override val lang: String,
    private val ehLang: String,
) : ConfigurableSource, HttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val name = "E-Hentai"

    override val baseUrl = "https://e-hentai.org"

    override val supportsLatest = true

    private var lastMangaId = ""

    // true if lang is a "natural human language"
    private fun isLangNatural(): Boolean = lang !in listOf("none", "other")

    private fun genericMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangaElements = doc.select("table.itg td.glname")
            .let { elements ->
                if (isLangNatural() && getEnforceLanguagePref()) {
                    elements.filter { element ->
                        // only accept elements with a language tag matching ehLang or without a language tag
                        // could make this stricter and not accept elements without a language tag, possibly add a sharedpreference for it
                        element.select("div[title^=language]").firstOrNull()?.let { it.text() == ehLang } ?: true
                    }
                } else {
                    elements
                }
            }
        val parsedMangas: MutableList<SManga> = mutableListOf()
        for (i in mangaElements.indices) {
            val manga = mangaElements[i].let {
                SManga.create().apply {
                    // Get title
                    it.select("a")?.first()?.apply {
                        title = this.select(".glink").text()
                        url = ExGalleryMetadata.normalizeUrl(attr("href"))
                        if (i == mangaElements.lastIndex) {
                            lastMangaId = ExGalleryMetadata.galleryId(attr("href"))
                        }
                    }
                    // Get image
                    it.parent()?.select(".glthumb img")?.first().apply {
                        thumbnail_url = this?.attr("data-src")?.nullIfBlank()
                            ?: this?.attr("src")
                    }
                }
            }
            parsedMangas.add(manga)
        }

        // Add to page if required
        val hasNextPage = doc.select("a#unext[href]").hasText()

        return MangasPage(parsedMangas, hasNextPage)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                chapter_number = 1f
            },
        ),
    )

    override fun fetchPageList(chapter: SChapter) = fetchChapterPage(chapter, "$baseUrl/${chapter.url}").map {
        it.mapIndexed { i, s ->
            Page(i, s)
        }
    }!!

    /**
     * Recursively fetch chapter pages
     */
    private fun fetchChapterPage(
        chapter: SChapter,
        np: String,
        pastUrls: List<String> = emptyList(),
    ): Observable<List<String>> {
        val urls = ArrayList(pastUrls)
        return chapterPageCall(np).flatMap {
            val jsoup = it.asJsoup()
            urls += parseChapterPage(jsoup)
            nextPageUrl(jsoup)?.let { string ->
                fetchChapterPage(chapter, string, urls)
            } ?: Observable.just(urls)
        }
    }

    private fun parseChapterPage(response: Element) = with(response) {
        select(".gdtm a").map {
            Pair(it.child(0).attr("alt").toInt(), it.attr("href"))
        }.sortedBy(Pair<Int, String>::first).map { it.second }
    }

    private fun chapterPageCall(np: String) = client.newCall(chapterPageRequest(np)).asObservableSuccess()
    private fun chapterPageRequest(np: String) = exGet(np, null, headers)

    private fun nextPageUrl(element: Element) = element.select("a[onclick=return false]").last()?.let {
        if (it.text() == ">") it.attr("href") else null
    }

    private fun languageTag(enforceLanguageFilter: Boolean = false): String {
        return if (enforceLanguageFilter || getEnforceLanguagePref()) "language:$ehLang" else ""
    }

    override fun popularMangaRequest(page: Int) = if (isLangNatural()) {
        exGet("$baseUrl/?f_search=${languageTag()}&f_srdd=5&f_sr=on", page)
    } else {
        latestUpdatesRequest(page)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val enforceLanguageFilter = filters.find { it is EnforceLanguageFilter }?.state == true
        val uri = Uri.parse("$baseUrl$QUERY_PREFIX").buildUpon()
        var modifiedQuery = when {
            !isLangNatural() -> query
            query.isBlank() -> languageTag(enforceLanguageFilter)
            else -> languageTag(enforceLanguageFilter).let { if (it.isNotEmpty()) "$query,$it" else query }
        }
        modifiedQuery += filters.filterIsInstance<TagFilter>()
            .flatMap { it.markedTags() }
            .joinToString(",")
            .let { if (it.isNotEmpty()) ",$it" else it }
        uri.appendQueryParameter("f_search", modifiedQuery)
        // when attempting to search with no genres selected, will auto select all genres
        filters.filterIsInstance<GenreGroup>().firstOrNull()?.state?.let {
            // variable to to check is any genres are selected
            val check = it.any { option -> option.state } // or it.any(GenreOption::state)
            // if no genres are selected by the user set all genres to on
            if (!check) {
                for (i in it) {
                    i.state = true
                }
            }
        }

        filters.forEach {
            if (it is UriFilter) it.addToUri(uri)
        }

        if (uri.toString().contains("f_spf") || uri.toString().contains("f_spt")) {
            if (page > 1) uri.appendQueryParameter("from", lastMangaId)
        }

        return exGet(uri.toString(), page)
    }

    override fun latestUpdatesRequest(page: Int) = exGet(baseUrl, page)

    override fun popularMangaParse(response: Response) = genericMangaParse(response)
    override fun searchMangaParse(response: Response) = genericMangaParse(response)
    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)

    private fun exGet(url: String, page: Int? = null, additionalHeaders: Headers? = null, cache: Boolean = true): Request {
        // pages no longer exist, if app attempts to go to the first page after a request, do not include the page append
        val pageIndex = if (page == 1) null else page
        return GET(
            pageIndex?.let {
                addParam(url, "next", lastMangaId)
            } ?: url,
            additionalHeaders?.let { header ->
                val headers = headers.newBuilder()
                header.toMultimap().forEach { (t, u) ->
                    u.forEach {
                        headers.add(t, it)
                    }
                }
                headers.build()
            } ?: headers,

        ).let {
            if (!cache) {
                it.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
            } else {
                it
            }
        }
    }

    /**
     * Parse gallery page to metadata model
     */
    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(response: Response) = with(response.asJsoup()) {
        with(ExGalleryMetadata()) {
            url = response.request.url.encodedPath
            title = select("#gn").text().nullIfBlank()?.trim()

            altTitle = select("#gj").text().nullIfBlank()?.trim()

            // Thumbnail is set as background of element in style attribute
            thumbnailUrl = select("#gd1 div").attr("style").nullIfBlank()?.let {
                it.substring(it.indexOf('(') + 1 until it.lastIndexOf(')'))
            }
            genre = select("#gdc div").text().nullIfBlank()?.trim()?.lowercase()

            uploader = select("#gdn").text().nullIfBlank()?.trim()

            // Parse the table
            select("#gdd tr").forEach {
                it.select(".gdt1")
                    .text()
                    .nullIfBlank()
                    ?.trim()
                    ?.let { left ->
                        it.select(".gdt2")
                            .text()
                            .nullIfBlank()
                            ?.trim()
                            ?.let { right ->
                                ignore {
                                    when (
                                        left.removeSuffix(":")
                                            .lowercase()
                                    ) {
                                        "posted" -> datePosted = EX_DATE_FORMAT.parse(right)?.time ?: 0
                                        "visible" -> visible = right.nullIfBlank()
                                        "language" -> {
                                            language = right.removeSuffix(TR_SUFFIX).trim().nullIfBlank()
                                            translated = right.endsWith(TR_SUFFIX, true)
                                        }
                                        "file size" -> size = parseHumanReadableByteCount(right)?.toLong()
                                        "length" -> length = right.removeSuffix("pages").trim().nullIfBlank()?.toInt()
                                        "favorited" -> favorites = right.removeSuffix("times").trim().nullIfBlank()?.toInt()
                                    }
                                }
                            }
                    }
            }

            // Parse ratings
            ignore {
                averageRating = select("#rating_label")
                    .text()
                    .removePrefix("Average:")
                    .trim()
                    .nullIfBlank()
                    ?.toDouble()
                ratingCount = select("#rating_count")
                    .text()
                    .trim()
                    .nullIfBlank()
                    ?.toInt()
            }

            // Parse tags
            tags.clear()
            select("#taglist tr").forEach {
                val namespace = it.select(".tc").text().removeSuffix(":")
                val currentTags = it.select("div").map { element ->
                    Tag(
                        element.text().trim(),
                        element.hasClass("gtl"),
                    )
                }
                tags[namespace] = currentTags
            }

            // Copy metadata to manga
            SManga.create().apply {
                copyTo(this)
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun imageUrlParse(response: Response): String = response.asJsoup().select("#img").attr("abs:src")

    private val cookiesHeader by lazy {
        val cookies = mutableMapOf<String, String>()

        // Setup settings
        val settings = mutableListOf<String>()

        // Do not show popular right now pane as we can't parse it
        settings += "prn_n"

        // Exclude every other language except the one we have selected
        settings += "xl_" + languageMappings.filter { it.first != ehLang }
            .flatMap { it.second }
            .joinToString("x")

        cookies["uconfig"] = buildSettings(settings)

        // Bypass "Offensive For Everyone" content warning
        cookies["nw"] = "1"

        buildCookies(cookies)
    }

    // Headers
    override fun headersBuilder() = super.headersBuilder().add("Cookie", cookiesHeader)

    private fun buildSettings(settings: List<String?>) = settings.filterNotNull().joinToString(separator = "-")

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    @Suppress("SameParameterValue")
    private fun addParam(url: String, param: String, value: String) = Uri.parse(url)
        .buildUpon()
        .appendQueryParameter(param, value)
        .toString()

    override val client = network.client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .addInterceptor { chain ->
            val newReq = chain
                .request()
                .newBuilder()
                .removeHeader("Cookie")
                .addHeader("Cookie", cookiesHeader)
                .build()

            chain.proceed(newReq)
        }.build()

    // Filters
    override fun getFilterList() = FilterList(
        EnforceLanguageFilter(getEnforceLanguagePref()),
        Watched(),
        GenreGroup(),
        TagFilter("Misc Tags", triStateBoxesFrom(miscTags), "other"),
        TagFilter("Female Tags", triStateBoxesFrom(femaleTags), "female"),
        TagFilter("Male Tags", triStateBoxesFrom(maleTags), "male"),
        AdvancedGroup(),
    )

    class Watched : CheckBox("Watched List"), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendPath("watched")
            }
        }
    }

    class GenreOption(name: String, private val genreId: String) : CheckBox(name, false), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("f_$genreId", if (state) "1" else "0")
        }
    }

    class GenreGroup : UriGroup<GenreOption>(
        "Genres",
        listOf(
            GenreOption("Dōjinshi", "doujinshi"),
            GenreOption("Manga", "manga"),
            GenreOption("Artist CG", "artistcg"),
            GenreOption("Game CG", "gamecg"),
            GenreOption("Western", "western"),
            GenreOption("Non-H", "non-h"),
            GenreOption("Image Set", "imageset"),
            GenreOption("Cosplay", "cosplay"),
            GenreOption("Asian Porn", "asianporn"),
            GenreOption("Misc", "misc"),
        ),
    )

    class AdvancedOption(name: String, private val param: String, defValue: Boolean = false) : CheckBox(name, defValue), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendQueryParameter(param, "on")
            }
        }
    }

    open class PageOption(name: String, private val queryKey: String) : Text(name), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state.isNotBlank()) {
                if (builder.build().getQueryParameters("f_sp").isEmpty()) {
                    builder.appendQueryParameter("f_sp", "on")
                }

                builder.appendQueryParameter(queryKey, state.trim())
            }
        }
    }

    class MinPagesOption : PageOption("Minimum Pages", "f_spf")
    class MaxPagesOption : PageOption("Maximum Pages", "f_spt")

    class RatingOption :
        Select<String>(
            "Minimum Rating",
            arrayOf(
                "Any",
                "2 stars",
                "3 stars",
                "4 stars",
                "5 stars",
            ),
        ),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state > 0) {
                builder.appendQueryParameter("f_srdd", (state + 1).toString())
                builder.appendQueryParameter("f_sr", "on")
            }
        }
    }

    // Explicit type arg for listOf() to workaround this: KT-16570
    class AdvancedGroup : UriGroup<Filter<*>>(
        "Advanced Options",
        listOf(
            AdvancedOption("Search Gallery Name", "f_sname", true),
            AdvancedOption("Search Gallery Tags", "f_stags", true),
            AdvancedOption("Search Gallery Description", "f_sdesc"),
            AdvancedOption("Search Torrent Filenames", "f_storr"),
            AdvancedOption("Only Show Galleries With Torrents", "f_sto"),
            AdvancedOption("Search Low-Power Tags", "f_sdt1"),
            AdvancedOption("Search Downvoted Tags", "f_sdt2"),
            AdvancedOption("Show Expunged Galleries", "f_sh"),
            RatingOption(),
            MinPagesOption(),
            MaxPagesOption(),
        ),
    )

    private class EnforceLanguageFilter(default: Boolean) : CheckBox("Enforce language", default)

    private val miscTags = "3d, already uploaded, anaglyph, animal on animal, animated, anthology, arisa mizuhara, artbook, ashiya noriko, bailey jay, body swap, caption, chouzuki maryou, christian godard, comic, compilation, dakimakura, fe galvao, ffm threesome, figure, forbidden content, full censorship, full color, game sprite, goudoushi, group, gunyou mikan, harada shigemitsu, hardcore, helly von valentine, higurashi rin, hololive, honey select, how to, incest, incomplete, ishiba yoshikazu, jessica nigri, kalinka fox, kanda midori, kira kira, kitami eri, kuroi hiroki, lenfried, lincy leaw, marie claude bourbonnais, matsunaga ayaka, me me me, missing cover, mmf threesome, mmt threesome, mosaic censorship, mtf threesome, multi-work series, no penetration, non-nude, novel, nudity only, oakazaki joe, out of order, paperchild, pm02 colon 20, poor grammar, radio comix, realporn, redraw, replaced, sakaki kasa, sample, saotome love, scanmark, screenshots, sinful goddesses, sketch lines, stereoscopic, story arc, takeuti ken, tankoubon, themeless, tikuma jukou, time stop, tsubaki zakuro, ttm threesome, twins, uncensored, vandych alex, variant set, watermarked, webtoon, western cg, western imageset, western non-h, yamato nadeshiko club, yui okada, yukkuri, zappa go"
    private val femaleTags = "ahegao, anal, angel, apron, bandages, bbw, bdsm, beauty mark, big areolae, big ass, big breasts, big clit, big lips, big nipples, bikini, blackmail, bloomers, blowjob, bodysuit, bondage, breast expansion, bukkake, bunny girl, business suit, catgirl, centaur, cheating, chinese dress, christmas, collar, corset, cosplaying, cowgirl, crossdressing, cunnilingus, dark skin, daughter, deepthroat, defloration, demon girl, double penetration, dougi, dragon, drunk, elf, exhibitionism, farting, females only, femdom, filming, fingering, fishnets, footjob, fox girl, furry, futanari, garter belt, ghost, giantess, glasses, gloves, goblin, gothic lolita, growth, guro, gyaru, hair buns, hairy, hairy armpits, handjob, harem, hidden sex, horns, huge breasts, humiliation, impregnation, incest, inverted nipples, kemonomimi, kimono, kissing, lactation, latex, leg lock, leotard, lingerie, lizard girl, maid, masked face, masturbation, midget, miko, milf, mind break, mind control, monster girl, mother, muscle, nakadashi, netorare, nose hook, nun, nurse, oil, paizuri, panda girl, pantyhose, piercing, pixie cut, policewoman, ponytail, pregnant, rape, rimjob, robot, scat, schoolgirl uniform, sex toys, shemale, sister, small breasts, smell, sole dickgirl, sole female, squirting, stockings, sundress, sweating, swimsuit, swinging, tail, tall girl, teacher, tentacles, thigh high boots, tomboy, transformation, twins, twintails, unusual pupils, urination, vore, vtuber, widow, wings, witch, wolf girl, x-ray, yuri, zombie"
    private val maleTags = "anal, bbm, big ass, big penis, bikini, blood, blowjob, bondage, catboy, cheating, chikan, condom, crab, crossdressing, dark skin, deepthroat, demon, dickgirl on male, dilf, dog boy, double anal, double penetration, dragon, drunk, exhibitionism, facial hair, feminization, footjob, fox boy, furry, glasses, group, guro, hairy, handjob, hidden sex, horns, huge penis, human on furry, kimono, lingerie, lizard guy, machine, maid, males only, masturbation, mmm threesome, monster, muscle, nakadashi, ninja, octopus, oni, pillory, policeman, possession, prostate massage, public use, schoolboy uniform, schoolgirl uniform, sex toys, shotacon, sleeping, snuff, sole male, stockings, sunglasses, swimsuit, tall man, tentacles, tomgirl, unusual pupils, virginity, waiter, x-ray, yaoi, zombie"

    private fun triStateBoxesFrom(tagString: String): List<TagTriState> = tagString.split(", ").map { TagTriState(it) }

    class TagTriState(tag: String) : TriState(tag)
    class TagFilter(name: String, private val triStateBoxes: List<TagTriState>, private val nameSpace: String) : Group<TagTriState>(name, triStateBoxes) {
        fun markedTags() = triStateBoxes.filter { it.isIncluded() }.map { "$nameSpace:${it.name}" } + triStateBoxes.filter { it.isExcluded() }.map { "-$nameSpace:${it.name}" }
    }

    // map languages to their internal ids
    private val languageMappings = listOf(
        Pair("japanese", listOf("0", "1024", "2048")),
        Pair("english", listOf("1", "1025", "2049")),
        Pair("chinese", listOf("10", "1034", "2058")),
        Pair("dutch", listOf("20", "1044", "2068")),
        Pair("french", listOf("30", "1054", "2078")),
        Pair("german", listOf("40", "1064", "2088")),
        Pair("hungarian", listOf("50", "1074", "2098")),
        Pair("italian", listOf("60", "1084", "2108")),
        Pair("korean", listOf("70", "1094", "2118")),
        Pair("polish", listOf("80", "1104", "2128")),
        Pair("portuguese", listOf("90", "1114", "2138")),
        Pair("russian", listOf("100", "1124", "2148")),
        Pair("spanish", listOf("110", "1134", "2158")),
        Pair("thai", listOf("120", "1144", "2168")),
        Pair("vietnamese", listOf("130", "1154", "2178")),
        Pair("n/a", listOf("254", "1278", "2302")),
        Pair("other", listOf("255", "1279", "2303")),
    )

    companion object {
        const val QUERY_PREFIX = "?f_apply=Apply+Filter"
        const val PREFIX_ID_SEARCH = "id:"
        const val TR_SUFFIX = "TR"

        // Preferences vals
        private const val ENFORCE_LANGUAGE_PREF_KEY = "ENFORCE_LANGUAGE"
        private const val ENFORCE_LANGUAGE_PREF_TITLE = "Enforce Language"
        private const val ENFORCE_LANGUAGE_PREF_SUMMARY = "If checked, forces browsing of manga matching a language tag"
        private const val ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE = false
    }

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val enforceLanguagePref = CheckBoxPreference(screen.context).apply {
            key = "${ENFORCE_LANGUAGE_PREF_KEY}_$lang"
            title = ENFORCE_LANGUAGE_PREF_TITLE
            summary = ENFORCE_LANGUAGE_PREF_SUMMARY
            setDefaultValue(ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${ENFORCE_LANGUAGE_PREF_KEY}_$lang", checkValue).commit()
            }
        }
        screen.addPreference(enforceLanguagePref)
    }

    private fun getEnforceLanguagePref(): Boolean = preferences.getBoolean("${ENFORCE_LANGUAGE_PREF_KEY}_$lang", ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE)
}
