package eu.kanade.tachiyomi.extension.all.mangadex

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AggregateVolumeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.NamedRelationshipDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.UserRelationshipDto
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.parser.Parser
import java.util.Locale
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

    private val tagNames by lazy { mdFilters.getTags(intl).associate { it.id to it.name } }

    /**
     * Turn HTML entities in manga or chapter names into characters. For example &hearts; will show ♥.
     */
    private fun String.removeEntities(): String = Parser.unescapeEntities(this, false)

    /**
     * Maps MangaDex status to Tachiyomi status.
     * Adapted from the MangaDex handler from TachiyomiSY.
     */
    private fun getPublicationStatus(attr: MangaAttributesDto, volumes: Map<String, AggregateVolumeDto>): Int {
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

    private fun List<Map<String, String>>.findTitleByLang(lang: String): String? = firstOrNull { it[lang] != null }?.values?.singleOrNull()

    /**
     * Create a [SManga] with only basic attributes filled.
     */
    fun createBasicManga(
        mangaDto: MangaDto,
        coverFileName: String?,
        coverSuffix: String?,
        lang: String,
        preferredTitle: String,
    ): SManga = SManga.create().apply {
        url = "/manga/${mangaDto.id}"

        val titleMap = mangaDto.attributes.title
        val altTitles = mangaDto.attributes.altTitles
        val romanizedLang = MDConstants.romanizedLangCodes[mangaDto.attributes.originalLanguage]
        val romanizedTitle = romanizedLang
            ?.takeIf { preferredTitle == MDConstants.TITLE_PREF_VAL_ROMANIZED }
            ?.let { titleMap[it] ?: altTitles.findTitleByLang(it) }
        val resolvedTitle = romanizedTitle ?: titleMap[lang] ?: run {
            val mainTitle = titleMap.values.firstOrNull()
            val langTitle = altTitles.findTitleByLang(lang)
            val enTitle = altTitles.findTitleByLang("en")

            if (preferredTitle == MDConstants.TITLE_PREF_VAL_EXTENSION) {
                listOfNotNull(langTitle, mainTitle, enTitle)
            } else {
                listOfNotNull(mainTitle, langTitle, enTitle)
            }.firstOrNull()
        }
        title = resolvedTitle?.removeEntities().orEmpty()

        if (coverFileName != null) {
            thumbnail_url = "${MDConstants.CDN_URL}/covers/${mangaDto.id}/$coverFileName${coverSuffix.orEmpty()}"
        }
    }

    /**
     * Create an [SManga] with all attributes filled.
     */
    fun createManga(
        mangaDto: MangaDto,
        chapters: Map<String, AggregateVolumeDto>,
        firstVolumeCover: String?,
        lang: String,
        coverSuffix: String?,
        preferredTitle: String,
    ): SManga {
        val attr = mangaDto.attributes

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

        val genresMap = attr.tags
            .groupBy({ it.group }) { tagNames[it.id] }
            .mapValues { it.value.filterNotNull().sortedWith(intl.collator) }

        val genreList = MDConstants.TAG_GROUPS_ORDER.flatMap { genresMap[it].orEmpty() } + nonGenres

        val altTitles = attr.altTitles
            .flatMap { it.entries }
            .map { (code, title) -> "[$code] ${title.removeEntities()}" }

        val finalChapter = listOfNotNull(
            attr.lastVolume?.takeIf { it.isNotEmpty() }?.let { "Vol.$it" },
            attr.lastChapter?.takeIf { it.isNotEmpty() }?.let { "Ch.$it" },
        )

        return createBasicManga(mangaDto, firstVolumeCover ?: mangaDto.coverFileName, coverSuffix, lang, preferredTitle).apply {
            description = MarkdownDescription.Builder()
                .markdown((attr.description[lang] ?: attr.description["en"])?.removeEntities())
                .altTitles(altTitles, intl["alternative_titles"])
                .field(intl["final_chapter"], finalChapter.joinToString(" "))
                .build()
            author = mangaDto.relationships.names(MDConstants.AUTHOR)
            artist = mangaDto.relationships.names(MDConstants.ARTIST)
            status = getPublicationStatus(attr, chapters)
            genre = genreList
                .filter(String::isNotEmpty)
                .joinToString()
        }
    }

    private fun List<*>.names(type: String) = filterIsInstance<NamedRelationshipDto>()
        .filter { it.type == type }
        .map { it.name }
        .distinct()
        .joinToString()

    /**
     * Create the [SChapter] from the JSON element.
     */
    fun createChapter(chapterDto: ChapterDto): SChapter {
        val attr = chapterDto.attributes

        val groups = chapterDto.relationships
            .filterIsInstance<NamedRelationshipDto>()
            .filter { it.type == MDConstants.SCANLATION_GROUP && it.id != MDConstants.LEGACY_NO_GROUP_ID } // 'no group' left over from MDv3
            .joinToString(" & ") { it.name }
            .ifEmpty {
                // Fallback to uploader name if no group is set.
                val users = chapterDto.relationships
                    .filterIsInstance<UserRelationshipDto>()
                    .map { it.username }
                if (users.isNotEmpty()) intl.format("uploaded_by", users.joinToString(" & ")) else ""
            }
            .ifEmpty { intl["no_group"] } // "No Group" as final resort

        val chapterName = mutableListOf<String>()

        attr.volume?.takeIf { it.isNotEmpty() }?.let { chapterName.add("Vol.$it") }
        attr.chapter?.takeIf { it.isNotEmpty() }?.let { chapterName.add("Ch.$it") }
        attr.title?.takeIf { it.isNotEmpty() }?.let {
            if (chapterName.isNotEmpty()) {
                chapterName.add("-")
            }
            chapterName.add(it)
        }

        // if volume, chapter and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }

        val unavailablePrefix = if (attr.isUnavailable) "${intl["chapter_unavailable_prefix"]} - " else ""
        val languagePrefix = if (lang == MDConstants.ALL_LANGUAGES) "[${attr.translatedLanguage}] " else ""

        return SChapter.create().apply {
            url = "/chapter/${chapterDto.id}"
            name = chapterName.joinToString(" ").removeEntities()
            date_upload = Instant.tryParse(attr.publishAt)
            scanlator = languagePrefix + unavailablePrefix + groups
            if (attr.isUnavailable) {
                memo = buildJsonObject { put(MDConstants.MEMO_UNAVAILABLE, true) }
            }
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
                        .all(MDConstants.uuidRegex::matches)

                    editText.error = if (!isValid) intl["invalid_uuids"] else null
                    editText.rootView.findViewById<Button>(android.R.id.button1)
                        ?.isEnabled = editText.error == null
                }
            },
        )
    }

    companion object {
        private val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        private val trailingHyphenRegex = "-+$".toRegex()
    }
}
