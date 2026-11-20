package eu.kanade.tachiyomi.extension.all.mangadex

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AggregateVolume
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ArtistDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AuthorArtistAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AuthorDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverArtAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverArtDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.EntityDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ListAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ListDataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaStatisticsDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ScanlationGroupAttributes
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ScanlationGroupDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.StatusDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.TagAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.TagDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UnknownEntity
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UserAttributes
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UserDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.utils.MangaMeta
import keiyoushi.utils.describe
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        prettyPrint = true
        serializersModule += SerializersModule {
            polymorphic(EntityDto::class) {
                subclass(AuthorDto::class)
                subclass(ArtistDto::class)
                subclass(ChapterDataDto::class)
                subclass(CoverArtDto::class)
                subclass(ListDataDto::class)
                subclass(MangaDataDto::class)
                subclass(ScanlationGroupDto::class)
                subclass(TagDto::class)
                subclass(UserDto::class)
                defaultDeserializer { UnknownEntity.serializer() }
            }

            polymorphic(AttributesDto::class) {
                subclass(AuthorArtistAttributesDto::class)
                subclass(ChapterAttributesDto::class)
                subclass(CoverArtAttributesDto::class)
                subclass(ListAttributesDto::class)
                subclass(MangaAttributesDto::class)
                subclass(ScanlationGroupAttributes::class)
                subclass(TagAttributesDto::class)
                subclass(UserAttributes::class)
            }
        }
    }

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
    fun getPublicationStatus(attr: MangaAttributesDto, volumes: Map<String, AggregateVolume>): Int {
        val chaptersList = volumes.values
            .flatMap { it.chapters.values }
            .map { it.chapter }

        val tempStatus = when (attr.status) {
            StatusDto.ONGOING -> SManga.ONGOING
            StatusDto.CANCELLED -> SManga.CANCELLED
            StatusDto.COMPLETED -> SManga.PUBLISHING_FINISHED
            StatusDto.HIATUS -> SManga.ON_HIATUS
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

        return response.parseAs<AtHomeDto>(json).baseUrl
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
        mangaDataDto: MangaDataDto,
        coverFileName: String?,
        coverSuffix: String?,
        lang: String,
        preferExtensionLangTitle: Boolean,
    ): SManga = SManga.create().apply {
        url = "/manga/${mangaDataDto.id}"

        val titleMap = mangaDataDto.attributes!!.title
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
        mangaDataDto: MangaDataDto,
        chapters: Map<String, AggregateVolume>,
        firstVolumeCover: String?,
        statistics: MangaStatisticsDto?,
        lang: String,
        coverSuffix: String?,
        altTitlesInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
        finalChapterInDesc: Boolean,
    ): SManga {
        val attr = mangaDataDto.attributes!!

        // Things that will go with the genre tags but aren't actually genre
        val dexLocale = Locale.forLanguageTag(lang)

        val nonGenres = listOfNotNull(
            attr.publicationDemographic
                ?.let { intl["publication_demographic_${it.name.lowercase()}"] },
            attr.contentRating
                .takeIf { it != ContentRatingDto.SAFE }
                ?.let { intl.format("content_rating_genre", intl["content_rating_${it.name.lowercase()}"]) },
            attr.originalLanguage
                ?.let { Locale.forLanguageTag(it) }
                ?.getDisplayName(dexLocale)
                ?.replaceFirstChar { it.uppercase(dexLocale) },
        )

        val authors = mangaDataDto.relationships
            .filterIsInstance<AuthorDto>()
            .mapNotNull { it.attributes?.name }
            .distinct()

        val artists = mangaDataDto.relationships
            .filterIsInstance<ArtistDto>()
            .mapNotNull { it.attributes?.name }
            .distinct()

        val coverFileName = firstVolumeCover ?: mangaDataDto.relationships
            .filterIsInstance<CoverArtDto>()
            .firstOrNull()
            ?.attributes?.fileName

        val tags = mdFilters.getTags(intl).associate { it.id to it.name }

        val genresMap = attr.tags
            .groupBy({ it.attributes!!.group }) { tagDto -> tags[tagDto.id] }
            .mapValues { it.value.filterNotNull().sortedWith(intl.collator) }

        val genreList = MDConstants.TAG_GROUPS_ORDER.flatMap { genresMap[it].orEmpty() } + nonGenres

        return createBasicManga(mangaDataDto, coverFileName, coverSuffix, lang, preferExtensionLangTitle).apply {
            author = authors.joinToString()
            artist = artists.joinToString()
            status = getPublicationStatus(attr, chapters)
            genre = genreList
                .filter(String::isNotEmpty)
                .joinToString()
            describe {
                // An unrated title still has MangaDex's starting score
                statistics?.rating?.let { stats -> stats.distribution.values.sum().takeIf { it > 0 }?.let { votes -> rating(stats.bayesian, votes = votes) } }
                description((attr.description[lang] ?: attr.description["en"])?.removeEntitiesAndMarkdown())
                if (altTitlesInDesc) {
                    val romanizedOriginalLang = MDConstants.romanizedLangCodes[attr.originalLanguage].orEmpty()
                    val allLanguages = lang == MDConstants.ALL_LANGUAGES
                    altTitles(
                        attr.altTitles
                            .filter { allLanguages || it.containsKey(lang) || it.containsKey(romanizedOriginalLang) }
                            .mapNotNull { it.entries.singleOrNull() }
                            .filter { it.value.isNotEmpty() }
                            .map { (code, title) -> (if (allLanguages) "[$code] $title" else title).removeEntities() },
                        intl["alternative_titles"],
                    )
                }
                if (finalChapterInDesc) {
                    val finalChapter = listOfNotNull(
                        attr.lastVolume?.takeIf { it.isNotEmpty() }?.let { "Vol.$it" },
                        attr.lastChapter?.takeIf { it.isNotEmpty() }?.let { "Ch.$it" },
                    ).joinToString(" ").removeEntities()
                    field(intl["final_chapter"], finalChapter)
                }
                meta {
                    // Every language's, whatever the setting prints
                    altTitles = attr.altTitles.flatMap { it.values }.map { it.removeEntities() }
                    contentRating = when (attr.contentRating) {
                        ContentRatingDto.SAFE -> MangaMeta.ContentRating.SAFE
                        ContentRatingDto.SUGGESTIVE -> MangaMeta.ContentRating.SUGGESTIVE
                        ContentRatingDto.EROTICA, ContentRatingDto.PORNOGRAPHIC -> MangaMeta.ContentRating.ADULT
                        null -> null
                    }
                    trackers(
                        MangaMeta.Tracker.ANILIST to attr.links["al"],
                        MangaMeta.Tracker.ANIMEPLANET to attr.links["ap"],
                        MangaMeta.Tracker.KITSU to attr.links["kt"],
                        MangaMeta.Tracker.MANGAUPDATES to attr.links["mu"],
                        MangaMeta.Tracker.NOVELUPDATES to attr.links["nu"],
                        MangaMeta.Tracker.MYANIMELIST to attr.links["mal"],
                    )
                    links(
                        listOf(
                            "BookWalker" to attr.links["bw"]?.takeIf(String::isNotBlank)?.let { "https://bookwalker.jp/$it" },
                            "Amazon" to attr.links["amz"],
                            "eBookJapan" to attr.links["ebj"],
                            "CDJapan" to attr.links["cdj"],
                        ),
                    )
                    links(listOf("Raw" to attr.links["raw"]), language = attr.originalLanguage?.toLanguageTag())
                    links(listOf("English" to attr.links["engtl"]), language = "en")
                }
            }
        }
    }

    // MangaDex's language codes as BCP 47 tags
    private fun String.toLanguageTag() = when (this) {
        "es-la" -> "es-419"
        "ja-ro" -> "ja-Latn"
        "ko-ro" -> "ko-Latn"
        "zh-ro" -> "zh-Latn"
        else -> this
    }

    /**
     * Create the [SChapter] from the JSON element.
     */
    fun createChapter(chapterDataDto: ChapterDataDto): SChapter {
        val attr = chapterDataDto.attributes!!

        val groups = chapterDataDto.relationships
            .filterIsInstance<ScanlationGroupDto>()
            .filterNot { it.id == MDConstants.LEGACY_NO_GROUP_ID } // 'no group' left over from MDv3
            .mapNotNull { it.attributes?.name }
            .joinToString(" & ")
            .ifEmpty {
                // Fallback to uploader name if no group is set.
                val users = chapterDataDto.relationships
                    .filterIsInstance<UserDto>()
                    .mapNotNull { it.attributes?.username }
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

        val unavailablePrefix = if (attr.isUnavailable == true) {
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
