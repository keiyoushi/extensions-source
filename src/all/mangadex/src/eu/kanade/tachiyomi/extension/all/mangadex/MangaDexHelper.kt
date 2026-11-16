package eu.kanade.tachiyomi.extension.all.mangadex

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AggregateVolumeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverRelationshipDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.NamedRelationshipDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UserRelationshipDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MangaDexHelper(private val lang: String) {

    val mdFilters = MangaDexFilters()

    val intl = Intl(
        language = lang,
        baseLanguage = MangaDexIntl.ENGLISH,
        availableLanguages = MangaDexIntl.AVAILABLE_LANGS,
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = { lang ->
            when (lang) {
                MangaDexIntl.SPANISH_LATAM -> Intl.createDefaultMessageFileName(MangaDexIntl.SPANISH)
                MangaDexIntl.PORTUGUESE -> Intl.createDefaultMessageFileName(MangaDexIntl.BRAZILIAN_PORTUGUESE)
                else -> Intl.createDefaultMessageFileName(lang)
            }
        },
    )

    /**
     * Gets the UUID from the url
     */
    fun getUUIDFromUrl(url: String) = url.substringAfterLast("/")

    /**
     * Get chapters for manga (aka manga/$id/feed endpoint)
     */
    fun getChapterEndpoint(mangaId: String, offset: Int) = "${MDConstants.API_MANGA_URL}/$mangaId/feed".toHttpUrl().newBuilder()
        .addQueryParameter("includes[]", MDConstants.SCANLATION_GROUP)
        .addQueryParameter("includes[]", MDConstants.USER)
        .addQueryParameter("limit", "500")
        .addQueryParameter("offset", offset.toString())
        .addQueryParameter("order[volume]", "desc")
        .addQueryParameter("order[chapter]", "desc")
        .addQueryParameter("includeFuturePublishAt", "0")
        .toString()

    /**
     * Check if the manga url is a valid uuid
     */
    fun containsUuid(url: String) = url.contains(MDConstants.uuidRegex)

    /**
     * Check if the string is a valid uuid
     */
    fun isUuid(text: String) = MDConstants.uuidRegex matches text

    /**
     * Get the manga offset pages are 1 based, so subtract 1
     */
    fun getMangaListOffset(page: Int): String = (MDConstants.MANGA_LIMIT * (page - 1)).toString()

    /**
     * Get the latest chapter offset pages are 1 based, so subtract 1
     */
    fun getLatestChapterOffset(page: Int): String = (MDConstants.LATEST_CHAPTER_LIMIT * (page - 1)).toString()

    /**
     * Remove any HTML characters in manga or chapter name to actual
     * characters. For example &hearts; will show ♥.
     */
    private fun String.removeEntities(): String = Parser.unescapeEntities(this, false)

    /**
     * Remove any HTML characters in description to actual characters.
     * It also removes Markdown syntax for links, italic and bold.
     */
    private fun String.removeEntitiesAndMarkdown(): String = removeEntities()
        .substringBefore("\n---")
        .replace(markdownLinksRegex, "$1")
        .replace(markdownItalicBoldRegex, "$1")
        .replace(markdownItalicRegex, "$1")
        .trim()

    /**
     * Maps MangaDex status to Tachiyomi status.
     * Adapted from the MangaDex handler from TachiyomiSY.
     */
    fun getPublicationStatus(attr: MangaAttributesDto, volumes: Map<String, AggregateVolumeDto>): Int {
        val chaptersList = volumes.values
            .flatMap { it.chapters.values }
            .map { it.chapter }

        val tempStatus = when (attr.status) {
            "ongoing" -> SManga.ONGOING
            "cancelled" -> SManga.CANCELLED
            "completed" -> SManga.PUBLISHING_FINISHED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        val publishedOrCancelled = tempStatus == SManga.PUBLISHING_FINISHED ||
            tempStatus == SManga.CANCELLED

        val isOneShot = attr.tags.any { it.id == MDConstants.TAG_ONE_SHOT_UUID } &&
            attr.tags.none { it.id == MDConstants.TAG_ANTHOLOGY_UUID }

        return when {
            chaptersList.contains(attr.lastChapter) && publishedOrCancelled -> SManga.COMPLETED
            isOneShot && volumes["none"]?.chapters?.get("none") != null -> SManga.COMPLETED
            else -> tempStatus
        }
    }

    /**
     * Chapter URL where we get the token, last request time.
     */
    private val tokenTracker = hashMapOf<String, Long>()

    companion object {
        val USE_CACHE = CacheControl.Builder()
            .maxStale(Int.MAX_VALUE.seconds)
            .build()

        val markdownLinksRegex = "\\[([^]]+)\\]\\(([^)]+)\\)".toRegex()
        val markdownItalicBoldRegex = "\\*+\\s*([^\\*]*)\\s*\\*+".toRegex()
        val markdownItalicRegex = "_+\\s*([^_]*)\\s*_+".toRegex()

        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()

        val trailingHyphenRegex = "-+$".toRegex()
    }

    /**
     * Check the token map to see if the MD@Home host is still valid.
     */
    fun getValidImageUrlForPage(page: Page, headers: Headers, client: OkHttpClient): Request {
        val (host, tokenRequestUrl, time) = page.url.split(",")

        val mdAtHomeServerUrl =
            when (System.currentTimeMillis() - time.toLong() > MDConstants.mdAtHomeTokenLifespan) {
                false -> host

                true -> {
                    val tokenLifespan = System.currentTimeMillis() - (tokenTracker[tokenRequestUrl] ?: 0)
                    val cacheControl = if (tokenLifespan > MDConstants.mdAtHomeTokenLifespan) {
                        CacheControl.FORCE_NETWORK
                    } else {
                        USE_CACHE
                    }
                    getMdAtHomeUrl(tokenRequestUrl, client, headers, cacheControl)
                }
            }

        return GET(mdAtHomeServerUrl + page.imageUrl, headers)
    }

    /**
     * Get the MD@Home URL.
     */
    private fun getMdAtHomeUrl(
        tokenRequestUrl: String,
        client: OkHttpClient,
        headers: Headers,
        cacheControl: CacheControl,
    ): String {
        val request = mdAtHomeRequest(tokenRequestUrl, headers, cacheControl)
        val response = client.newCall(request).execute()

        // This check is for the error that causes pages to fail to load.
        // It should never be entered, but in case it is, we retry the request.
        if (response.code == 504) {
            Log.wtf("MangaDex", "Failed to read cache for \"$tokenRequestUrl\"")
            return getMdAtHomeUrl(tokenRequestUrl, client, headers, CacheControl.FORCE_NETWORK)
        }

        return response.parseAs<AtHomeDto>().baseUrl
    }

    /**
     * create an md at home Request
     */
    fun mdAtHomeRequest(
        tokenRequestUrl: String,
        headers: Headers,
        cacheControl: CacheControl,
    ): Request {
        if (cacheControl == CacheControl.FORCE_NETWORK) {
            tokenTracker[tokenRequestUrl] = System.currentTimeMillis()
        }

        return GET(tokenRequestUrl, headers, cacheControl).newBuilder().fetch(Fetch.XHR).build()
    }

    fun mdAtHomeRefresh(tokenRequestUrl: String) {
        tokenTracker[tokenRequestUrl] = System.currentTimeMillis()
    }

    private fun List<Map<String, String>>.findTitleByLang(lang: String): String? = firstOrNull { it[lang] != null }?.values?.singleOrNull()

    /**
     * Create a [SManga] from the JSON element with only basic attributes filled.
     */
    fun createBasicManga(
        mangaDataDto: MangaDto,
        coverFileName: String?,
        coverSuffix: String?,
        lang: String,
        preferExtensionLangTitle: Boolean,
    ): SManga = SManga.create().apply {
        url = "/manga/${mangaDataDto.id}"

        val titleMap = mangaDataDto.attributes.title
        title = with(mangaDataDto.attributes) {
            titleMap[lang] ?: altTitles.run {
                val mainTitle = titleMap.values.firstOrNull()
                val langTitle = findTitleByLang(lang)
                val enTitle = findTitleByLang("en")

                if (preferExtensionLangTitle) {
                    listOf(langTitle, mainTitle, enTitle)
                } else {
                    listOf(mainTitle, langTitle, enTitle)
                }.firstNotNullOfOrNull { it }
            }
        }?.removeEntities().orEmpty()

        coverFileName?.let {
            thumbnail_url = when (!coverSuffix.isNullOrEmpty()) {
                true -> "${MDConstants.CDN_URL}/covers/${mangaDataDto.id}/$coverFileName$coverSuffix"
                else -> "${MDConstants.CDN_URL}/covers/${mangaDataDto.id}/$coverFileName"
            }
        }
    }

    /**
     * Create an [SManga] from the JSON element with all attributes filled.
     */
    fun createManga(
        mangaDataDto: MangaDto,
        chapters: Map<String, AggregateVolumeDto>,
        firstVolumeCover: String?,
        lang: String,
        coverSuffix: String?,
        altTitlesInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
        finalChapterInDesc: Boolean,
    ): SManga {
        val attr = mangaDataDto.attributes

        // Things that will go with the genre tags but aren't actually genre
        val dexLocale = Locale.forLanguageTag(lang)

        val nonGenres = listOfNotNull(
            attr.publicationDemographic
                ?.let { intl["publication_demographic_$it"] },
            attr.contentRating
                .takeIf { it != MDConstants.CONTENT_RATING_PREF_VAL_SAFE }
                ?.let { intl.format("content_rating_genre", intl["content_rating_$it"]) },
            Locale.forLanguageTag(attr.originalLanguage)
                .getDisplayName(dexLocale)
                .replaceFirstChar { it.uppercase(dexLocale) },
        )

        val authors = mangaDataDto.relationships
            .filterIsInstance<NamedRelationshipDto>()
            .filter { it.type == MDConstants.AUTHOR }
            .map { it.name }
            .distinct()

        val artists = mangaDataDto.relationships
            .filterIsInstance<NamedRelationshipDto>()
            .filter { it.type == MDConstants.ARTIST }
            .map { it.name }
            .distinct()

        val coverFileName = firstVolumeCover ?: mangaDataDto.relationships
            .firstInstanceOrNull<CoverRelationshipDto>()
            ?.fileName

        val tags = mdFilters.getTags(intl).associate { it.id to it.name }

        val genresMap = attr.tags
            .groupBy({ it.group }) { tagDto -> tags[tagDto.id] }
            .mapValues { it.value.filterNotNull().sortedWith(intl.collator) }

        val genreList = MDConstants.TAG_GROUPS_ORDER.flatMap { genresMap[it].orEmpty() } + nonGenres

        // Build description
        val desc = mutableListOf<String>()

        (attr.description[lang] ?: attr.description["en"])
            ?.removeEntitiesAndMarkdown()
            ?.let { desc.add(it) }

        if (altTitlesInDesc) {
            val romanizedOriginalLang = MDConstants.romanizedLangCodes[attr.originalLanguage].orEmpty()
            val allLanguages = lang == MDConstants.ALL_LANGUAGES
            val altTitles = attr.altTitles
                .filter { allLanguages || it.containsKey(lang) || it.containsKey(romanizedOriginalLang) }
                .mapNotNull { it.entries.singleOrNull() }
                .filter { it.value.isNotEmpty() }
                .map { (code, title) -> if (allLanguages) "[$code] $title" else title }

            if (altTitles.isNotEmpty()) {
                val altTitlesDesc = altTitles
                    .joinToString("\n", "${intl["alternative_titles"]}\n") { "• $it" }
                desc.add(altTitlesDesc.removeEntities())
            }
        }

        if (finalChapterInDesc) {
            val finalChapter = mutableListOf<String>()
            attr.lastVolume?.takeIf { it.isNotEmpty() }?.let { finalChapter.add("Vol.$it") }
            attr.lastChapter?.takeIf { it.isNotEmpty() }?.let { finalChapter.add("Ch.$it") }

            if (finalChapter.isNotEmpty()) {
                val finalChapterDesc = finalChapter
                    .joinToString(" ", "${intl["final_chapter"]}\n")
                desc.add(finalChapterDesc.removeEntities())
            }
        }

        return createBasicManga(mangaDataDto, coverFileName, coverSuffix, lang, preferExtensionLangTitle).apply {
            description = desc.joinToString("\n\n")
            author = authors.joinToString()
            artist = artists.joinToString()
            status = getPublicationStatus(attr, chapters)
            genre = genreList
                .filter(String::isNotEmpty)
                .joinToString()
        }
    }

    /**
     * Create the [SChapter] from the JSON element.
     */
    fun createChapter(chapterDataDto: ChapterDto): SChapter {
        val attr = chapterDataDto.attributes

        val groups = chapterDataDto.relationships
            .filterIsInstance<NamedRelationshipDto>()
            .filterNot { it.id == MDConstants.LEGACY_NO_GROUP_ID } // 'no group' left over from MDv3
            .map { it.name }
            .joinToString(" & ")
            .ifEmpty {
                // Fallback to uploader name if no group is set.
                val users = chapterDataDto.relationships
                    .filterIsInstance<UserRelationshipDto>()
                    .map { it.username }
                if (users.isNotEmpty()) intl.format("uploaded_by", users.joinToString(" & ")) else ""
            }
            .ifEmpty { intl["no_group"] } // "No Group" as final resort

        val chapterName = mutableListOf<String>()
        // Build chapter name

        attr.volume?.let {
            if (it.isNotEmpty()) {
                chapterName.add("Vol.$it")
            }
        }

        attr.chapter?.let {
            if (it.isNotEmpty()) {
                chapterName.add("Ch.$it")
            }
        }

        attr.title?.let {
            if (it.isNotEmpty()) {
                if (chapterName.isNotEmpty()) {
                    chapterName.add("-")
                }
                chapterName.add(it)
            }
        }

        // if volume, chapter and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }

        // In future calculate [END] if non mvp api doesn't provide it

        val unavailablePrefix = if (attr.isUnavailable) {
            intl["chapter_unavailable_prefix"] + " - "
        } else {
            ""
        }

        val languagePrefix = if (lang == MDConstants.ALL_LANGUAGES) "[${attr.translatedLanguage}] " else ""

        return SChapter.create().apply {
            url = "/chapter/${chapterDataDto.id}"
            name = chapterName.joinToString(" ").removeEntities()
            date_upload = Instant.tryParse(attr.publishAt)
            scanlator = languagePrefix + unavailablePrefix + groups
        }
    }

    fun titleToSlug(title: String) = title.trim()
        .lowercase(Locale.US)
        .replace(titleSpecialCharactersRegex, "-")
        .replace(trailingHyphenRegex, "")
        .split("-")
        .reduce { accumulator, element ->
            val currentSlug = "$accumulator-$element"
            if (currentSlug.length > 100) {
                accumulator
            } else {
                currentSlug
            }
        }

    /**
     * Adds a custom [TextWatcher] to the preference's [EditText] that show an
     * error if the input value contains invalid UUIDs. If the validation fails,
     * the Ok button is disabled to prevent the user from saving the value.
     *
     * This will likely need to be removed or revisited when the app migrates the
     * extension preferences screen to Compose.
     */
    fun setupEditTextUuidValidator(editText: EditText) {
        editText.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(editable: Editable?) {
                    requireNotNull(editable)

                    val text = editable.toString()

                    val isValid = text.isBlank() || text
                        .split(",")
                        .map(String::trim)
                        .all(::isUuid)

                    editText.error = if (!isValid) intl["invalid_uuids"] else null
                    editText.rootView.findViewById<Button>(android.R.id.button1)
                        ?.isEnabled = editText.error == null
                }
            },
        )
    }
}
