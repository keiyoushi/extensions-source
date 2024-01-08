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
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ScanlationGroupAttributes
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ScanlationGroupDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.StatusDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.TagAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.TagDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UnknownEntity
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UserAttributes
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UserDto
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
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
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaDexHelper(lang: String) {

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
    fun getChapterEndpoint(mangaId: String, offset: Int, langCode: String) =
        "${MDConstants.apiMangaUrl}/$mangaId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", MDConstants.scanlationGroup)
            .addQueryParameter("includes[]", MDConstants.user)
            .addQueryParameter("limit", "500")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("translatedLanguage[]", langCode)
            .addQueryParameter("order[volume]", "desc")
            .addQueryParameter("order[chapter]", "desc")
            .addQueryParameter("includeFuturePublishAt", "0")
            .addQueryParameter("includeEmptyPages", "0")
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
    fun getMangaListOffset(page: Int): String = (MDConstants.mangaLimit * (page - 1)).toString()

    /**
     * Get the latest chapter offset pages are 1 based, so subtract 1
     */
    fun getLatestChapterOffset(page: Int): String =
        (MDConstants.latestChapterLimit * (page - 1)).toString()

    /**
     * Remove any HTML characters in manga or chapter name to actual
     * characters. For example &hearts; will show ♥.
     */
    private fun String.removeEntities(): String {
        return Parser.unescapeEntities(this, false)
    }

    /**
     * Remove any HTML characters in description to actual characters.
     * It also removes Markdown syntax for links, italic and bold.
     */
    private fun String.removeEntitiesAndMarkdown(): String {
        return removeEntities()
            .substringBefore("---")
            .replace(markdownLinksRegex, "$1")
            .replace(markdownItalicBoldRegex, "$1")
            .replace(markdownItalicRegex, "$1")
            .trim()
    }

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

        val isOneShot = attr.tags.any { it.id == MDConstants.tagOneShotUuid } &&
            attr.tags.none { it.id == MDConstants.tagAnthologyUuid }

        return when {
            chaptersList.contains(attr.lastChapter) && publishedOrCancelled -> SManga.COMPLETED
            isOneShot && volumes["none"]?.chapters?.get("none") != null -> SManga.COMPLETED
            else -> tempStatus
        }
    }

    private fun parseDate(dateAsString: String): Long =
        MDConstants.dateFormatter.parse(dateAsString)?.time ?: 0

    /**
     * Chapter URL where we get the token, last request time.
     */
    private val tokenTracker = hashMapOf<String, Long>()

    companion object {
        val USE_CACHE = CacheControl.Builder()
            .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
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
            when (Date().time - time.toLong() > MDConstants.mdAtHomeTokenLifespan) {
                false -> host
                true -> {
                    val tokenLifespan = Date().time - (tokenTracker[tokenRequestUrl] ?: 0)
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

        return response.use { json.decodeFromString<AtHomeDto>(it.body.string()).baseUrl }
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
            tokenTracker[tokenRequestUrl] = Date().time
        }

        return GET(tokenRequestUrl, headers, cacheControl)
    }

    /**
     * Create a [SManga] from the JSON element with only basic attributes filled.
     */
    fun createBasicManga(
        mangaDataDto: MangaDataDto,
        coverFileName: String?,
        coverSuffix: String?,
        lang: String,
    ): SManga = SManga.create().apply {
        url = "/manga/${mangaDataDto.id}"
        val titleMap = mangaDataDto.attributes!!.title
        val dirtyTitle =
            titleMap.values.firstOrNull() // use literally anything from title as first resort
                ?: mangaDataDto.attributes.altTitles
                    .find { (it[lang] ?: it["en"]) !== null }
                    ?.values?.singleOrNull() // find something else from alt titles
        title = dirtyTitle?.removeEntities().orEmpty()

        coverFileName?.let {
            thumbnail_url = when (!coverSuffix.isNullOrEmpty()) {
                true -> "${MDConstants.cdnUrl}/covers/${mangaDataDto.id}/$coverFileName$coverSuffix"
                else -> "${MDConstants.cdnUrl}/covers/${mangaDataDto.id}/$coverFileName"
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
        lang: String,
        coverSuffix: String?,
        altTitlesInDesc: Boolean,
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

        val genreList = MDConstants.tagGroupsOrder.flatMap { genresMap[it].orEmpty() } + nonGenres

        var desc = (attr.description[lang] ?: attr.description["en"])
            ?.removeEntitiesAndMarkdown()
            .orEmpty()

        if (altTitlesInDesc) {
            val romanizedOriginalLang = MDConstants.romanizedLangCodes[attr.originalLanguage].orEmpty()
            val altTitles = attr.altTitles
                .filter { it.containsKey(lang) || it.containsKey(romanizedOriginalLang) }
                .mapNotNull { it.values.singleOrNull() }
                .filter(String::isNotEmpty)

            if (altTitles.isNotEmpty()) {
                val altTitlesDesc = altTitles
                    .joinToString("\n", "${intl["alternative_titles"]}\n") { "• $it" }
                desc += (if (desc.isBlank()) "" else "\n\n") + altTitlesDesc.removeEntities()
            }
        }

        return createBasicManga(mangaDataDto, coverFileName, coverSuffix, lang).apply {
            description = desc
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
    fun createChapter(chapterDataDto: ChapterDataDto): SChapter {
        val attr = chapterDataDto.attributes!!

        val groups = chapterDataDto.relationships
            .filterIsInstance<ScanlationGroupDto>()
            .filterNot { it.id == MDConstants.legacyNoGroupId } // 'no group' left over from MDv3
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

        return SChapter.create().apply {
            url = "/chapter/${chapterDataDto.id}"
            name = chapterName.joinToString(" ").removeEntities()
            date_upload = parseDate(attr.publishAt)
            scanlator = groups
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
