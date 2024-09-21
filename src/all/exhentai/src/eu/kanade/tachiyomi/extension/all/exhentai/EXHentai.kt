package eu.kanade.tachiyomi.extension.all.exhentai

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
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

abstract class EXHentai(
    override val lang: String,
    private val ehLang: String,
) : ConfigurableSource, HttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    private val webViewCookieManager: CookieManager by lazy { CookieManager.getInstance() }
    override val name = "EXHentai"

    private val memberId: String = getMemberIdPref()
    private val passHash: String = getPassHashPref()

    override val baseUrl: String
        get() = if (memberId.isEmpty() || passHash.isEmpty()) {
            "https://forums.e-hentai.org/index.php?act=Login"
        } else {
            "https://exhentai.org"
        }

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
                        thumbnail_url = this?.attr("data-src")?.takeIf { it.isNotBlank() } ?: this?.attr("src")
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

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

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

        // Check if either value is empty and throw an exception if true
        if (memberId.isEmpty() || passHash.isEmpty()) {
            throw IllegalArgumentException("Login with WebView, stay on website for 10 second and restart the app")
        }

        // Add ipb_member_id and ipb_pass_hash cookies
        cookies["ipb_member_id"] = memberId
        cookies["ipb_pass_hash"] = passHash
        cookies["igneous"] = ""

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

    private val miscTags = "3d, 3d imageset, abortion, absorption, additional eyes, adventitious mouth, adventitious penis, adventitious vagina, afrikaans, afro, age progression, age regression, ahegao, ai generated, albanian, albino, all the way through, already uploaded, amputee, anaglyph, anal, anal birth, anal intercourse, anal prolapse, analphagia, angel, animal on animal, animal on furry, animated, animegao, anorexic, anthology, apparel bukkake, apron, arabic, aramaic, armenian, armpit licking, armpit sex, artbook, asphyxiation, ass expansion, assjob, autofellatio, bald, ball caressing, ball sucking, balljob, balls expansion, bandages, bdsm, bear, beauty mark, bengali, big areolae, big ass, big balls, big breasts, big lips, big muscles, big nipples, big penis, bike shorts, bikini, bisexual, blackmail, blind, blindfold, blood, bloomers, blowjob, blowjob face, body modification, body painting, body swap, body writing, bodystocking, bodysuit, bondage, bosnian, braces, brain fuck, breast expansion, breast feeding, bride, bukkake, bulgarian, burmese, burping, business suit, butler, camel, cannibalism, caption, cashier, cat, catalan, cbt, cebuano, centaur, cervix prolapse, chastity belt, cheating, cheerleader, chikan, chinese, chinese dress, chloroform, christmas, clamp, clit insertion, clit stimulation, cloaca insertion, clone, closed eyes, clothed paizuri, clown, coach, cock ring, cockphagia, cockslapping, collar, comic, compilation, condom, confinement, conjoined, coprophagia, corpse, corruption, corset, cosplaying, cousin, crab, cree, creole, croatian, crossdressing, crotch tattoo, crown, crying, cum bath, cum in eye, cumflation, cunnilingus, cuntbusting, czech, dakimakura, danish, dark nipples, dark sclera, dark skin, deepthroat, deer, defaced, denki anma, detached sleeves, diaper, dicknipples, dinosaur, dismantling, dog, doll joints, dolphin, domination loss, donkey, double anal, double blowjob, double penetration, dougi, draenei, dragon, drill hair, drugs, drunk, dutch, ear fuck, eel, eggs, electric shocks, elephant, elf, emotionless sex, enema, english, esperanto, estonian, exhibitionism, exposed clothing, extraneous ads, eye penetration, eye-covering bang, eyemask, eyepatch, facesitting, facial hair, fairy, fanny packing, farting, ffm threesome, figure, filming, fingering, finnish, first person perspective, fish, fishnets, fisting, focus anal, focus blowjob, focus paizuri, food on body, foot insertion, foot licking, footjob, forbidden content, forced exposure, forniphilia, fox, freckles, french, frog, frottage, full censorship, full color, full tour, fundoshi, furry, gag, galleries, game sprite, gang rape, gaping, garter belt, gasmask, gender change, gender morph, genital piercing, georgian, german, ghost, giant sperm, gijinka, glasses, glory hole, gloves, goat, goblin, gokkun, gorilla, gothic lolita, goudoushi, greek, group, growth, gujarati, gymshorts, haigure, hair buns, hairjob, hairy, hairy armpits, halo, handicapped, handjob, hanging, hardcore, harem, harness, harpy, headless, headphones, hebrew, heterochromia, hidden sex, high heels, hijab, hindi, hmong, hood, horns, horse, horse cock, hotpants, how to, huge penis, human cattle, human on furry, humiliation, hungarian, icelandic, impregnation, incest, incomplete, indonesian, infantilism, inflation, insect, inseki, internal urination, inverted nipples, invisible, irish, italian, japanese, javanese, kangaroo, kannada, kappa, kazakh, kemonomimi, khmer, kigurumi pajama, kimono, kindergarten uniform, kissing, kodomo doushi, kodomo only, korean, kunoichi, kurdish, lab coat, lactation, ladino, lao, large insertions, large tattoo, latex, latin, latvian, layer cake, leash, leg lock, legjob, leotard, lingerie, lipstick mark, living clothes, long tongue, low bestiality, low guro, low incest, low scat, low smegma, machine, maggot, magical girl, maid, makeup, marathi, masked face, masturbation, mesugaki, mesuiki‎, metal armor, midget, miko, military, milking, mind break, mind control, missing cover, mmf threesome, mmt threesome, mongolian, monkey, monoeye, moral degeneration, mosaic censorship, mouse, mouth mask, mtf threesome, multi-work series‎, multimouth blowjob, multipanel sequence, multiple arms, multiple assjob, multiple footjob, multiple handjob, multiple nipples, multiple orgasms, multiple penises, multiple straddling, muscle, muscle growth, mute, nakadashi, navel birth, navel fuck, nazi, ndebele, necrophilia, nepali, netorare, netorase, nipple birth, nipple fuck, nipple piercing, nipple stimulation, no penetration, non-h game manual, non-h imageset, non-nude, norwegian, nose fuck, nose hook, novel, nudism, nudity only, nun, nurse, object insertion only, octopus, oil, omorashi, onahole, oni, orc, orgasm denial, oromo, ostrich, out of order, oyakodon‎, painted nails, paizuri, panther, pantyhose, pantyjob, paperchild, papiamento, parasite, pashto, pasties, pegasus, penis birth, penis enlargement, persian, personality excretion, petplay, petrification, phimosis, phone sex, piercing, pig, pillory, pirate, piss drinking, pixie cut, pole dancing, polish, ponygirl, ponytail, portuguese, possession, pregnant, prehensile hair, prolapse, prostate massage, prostitution, pubic stubble, public use, punjabi, rabbit, randoseru, rape, realporn, redraw, replaced, reptile, retractable penis, rewrite, rhinoceros, rimjob, robot, romanian, rough grammar, rough translation, russian, ryona, saliva, sample, sango, sanskrit, sarashi, scanmark, scar, scat, scat insertion, school gym uniform, school swimsuit, schoolboy uniform, schoolgirl uniform, screenshots, scrotal lingerie, selfcest, serbian, sex toys, shapening, shared senses, shark, shaved head, sheep, shibari, shimaidon, shimapan, shona, shrinking, sketch lines, skinsuit, slave, sleeping, slime, slovak, slovenian, slug, small penis, smalldom, smell, smoking, snake, snuff, sockjob, solo action, somali, soushuuhen, spanish, spanking, speculum, speechless, spider, stereoscopic, stewardess, stirrup legwear, stockings, stomach deformation, story arc, straitjacket, strap-on, stretching, stuck in wall, sumata, sundress, sunglasses, swahili, sweating, swedish, swimsuit, swinging, syringe, tabi socks, table masturbation, tagalog, tail, tail plug, tailjob, tailphagia, tamil, tankoubon, tanlines, teacher, telugu, tentacles, text cleaned, thai, themeless, thick eyebrows, thigh high boots, tiara, tibetan, tickling, tiger, tights, tigrinya, time stop, toddlercon, tooth brushing, torture, tracksuit, trampling, transformation, translated, transparent clothing, triple anal, triple penetration, ttm threesome, tube, turkish, turtle, tutor, twins, twintails, ukrainian, unbirth, uncensored, underwater, unicorn, unusual insertions, unusual pupils, unusual teeth, urdu, urethra insertion, urination, vacbed, vaginal birth, vampire, variant set, various, very long hair, vietnamese, vomit, vore, voyeurism, vtuber, waiter, waitress, watermarked, webtoon, weight gain, welsh, western cg, western imageset, western non-h‎, wet clothes, whale, whip, wingjob, wings, witch, wolf, wooden horse, worm, wormhole, wrestling, x-ray, yandere, yiddish, yukkuri, zebra, zombie, zulu"
    private val femaleTags = "adventitious vagina, alien girl, aunt, autopaizuri, ball-less shemale, bandaid, bat girl, bbw, bear girl, bee girl, big clit, big vagina, bird girl, breast reduction, bunny girl, catfight, catgirl, cervix penetration, clit growth, clothed male nude female, cow, cowgirl, cum in eye, cum swap, cuntbusting, daughter, deer girl, defloration, demon girl, dickgirl on dickgirl, dickgirl on female, dickgirls only, dog girl, double vaginal, elephant girl, females only, femdom, fff threesome, fft threesome, fox girl, frog girl, full-packaged futanari, futanari, futanarization, giantess, gigantic breasts, giraffe girl‎, granddaughter, grandmother, gyaru, horse girl, huge breasts, hyena girl, insect girl, kangaroo girl‎, kneepit sex, lioness, lizard girl, lolicon, low lolicon, male on dickgirl, mecha girl, menstruation, mermaid, milf, minigirl, monkey girl, monster girl, mother, mouse girl, multiple breasts, multiple nipples, multiple paizuri, multiple vaginas, niece, nipple expansion, old lady, oppai loli, otter girl, panda girl, pig girl, plant girl, policewoman, ponygirl, raccoon girl‎, race queen, real doll, shark girl, sheep girl, shemale, sister, skunk girl, slime girl, small breasts, snail girl, snake girl, sole dickgirl, sole female, spider girl, squid girl, squirrel girl, squirting, ssbbw, tall girl, tomboy, tribadism, triple vaginal, ttf threesome, ttt threesome, vaginal sticker, widow, wolf girl, yuri"
    private val maleTags = "alien, bat boy, bbm, bear boy, bee boy, bird boy, brother, bull, bunny boy, catboy, clothed female nude male, cowman, cuntboy, deer boy, demon, dickgirl on male, dilf, dog boy, elephant boy, father, feminization, fox boy, frog boy, giant, giraffe boy‎, grandfather, gyaru-oh‎, horse boy, hyena boy, insect boy, josou seme, kangaroo boy, lion, lizard guy, low shotacon, males only, mecha boy‎, merman, miniguy, minotaur, mmm threesome, monkey boy, monster, mouse boy, ninja, no balls, old man, otokofutanari, otter boy, panda boy, pegging, pig man, plant boy, policeman, priest, pussyboys only, raccoon boy, shark boy, sheep boy, skunk boy, slime boy‎, shotacon, snake boy, sole male, sole pussyboy, spider boy, squid boy, squirrel boy, ssbbm, steward, tall man, tomgirl, uncle, virginity, widower, wolf boy, yaoi"
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
        private const val TOAST_RESTART = "Please restart tachiyomi"

        // Preferences vals
        private const val ENFORCE_LANGUAGE_PREF_KEY = "ENFORCE_LANGUAGE"
        private const val ENFORCE_LANGUAGE_PREF_TITLE = "Enforce Language"
        private const val ENFORCE_LANGUAGE_PREF_SUMMARY = "If checked, forces browsing of manga matching a language tag"
        private const val ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE = false

        private const val MEMBER_ID_PREF_KEY = "MEMBER_ID"
        private const val MEMBER_ID_PREF_TITLE = "ipb_member_id"
        private const val MEMBER_ID_PREF_SUMMARY = "ipb_member_id value"
        private const val MEMBER_ID_PREF_DEFAULT_VALUE = ""

        private const val PASS_HASH_PREF_KEY = "PASS_HASH"
        private const val PASS_HASH_PREF_TITLE = "ipb_pass_hash"
        private const val PASS_HASH_PREF_SUMMARY = "ipb_pass_hash value"
        private const val PASS_HASH_PREF_DEFAULT_VALUE = ""
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
        val memberIdPref = EditTextPreference(screen.context).apply {
            key = MEMBER_ID_PREF_KEY
            title = MEMBER_ID_PREF_TITLE
            summary = MEMBER_ID_PREF_SUMMARY

            setDefaultValue(MEMBER_ID_PREF_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting =
                        preferences.edit().putString(MEMBER_ID_PREF_KEY, newValue as String).commit()
                    Toast.makeText(screen.context, TOAST_RESTART, Toast.LENGTH_LONG).show()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
        val passHashPref = EditTextPreference(screen.context).apply {
            key = PASS_HASH_PREF_KEY
            title = PASS_HASH_PREF_TITLE
            summary = PASS_HASH_PREF_SUMMARY

            setDefaultValue(PASS_HASH_PREF_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(PASS_HASH_PREF_KEY, newValue as String).commit()
                    Toast.makeText(screen.context, TOAST_RESTART, Toast.LENGTH_LONG).show()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
        screen.addPreference(enforceLanguagePref)
        screen.addPreference(memberIdPref)
        screen.addPreference(passHashPref)
    }

    private fun getEnforceLanguagePref(): Boolean = preferences.getBoolean("${ENFORCE_LANGUAGE_PREF_KEY}_$lang", ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE)
    private fun getCookieValue(cookieTitle: String, defaultValue: String, prefKey: String): String {
        val cookies = webViewCookieManager.getCookie("https://forums.e-hentai.org")
        var value: String? = null

        if (cookies != null) {
            val cookieArray = cookies.split("; ")
            for (cookie in cookieArray) {
                if (cookie.startsWith("$cookieTitle=")) {
                    value = cookie.split("=")[1]

                    break
                }
            }
        }

        if (value == null) {
            value = preferences.getString(prefKey, defaultValue) ?: defaultValue
        }

        return value
    }

    private fun getPassHashPref(): String {
        return getCookieValue(PASS_HASH_PREF_TITLE, PASS_HASH_PREF_DEFAULT_VALUE, PASS_HASH_PREF_KEY)
    }

    private fun getMemberIdPref(): String {
        return getCookieValue(MEMBER_ID_PREF_TITLE, MEMBER_ID_PREF_DEFAULT_VALUE, MEMBER_ID_PREF_KEY)
    }
}
