package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.extension.all.namicomi.dto.AbstractTagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.AttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterAttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.CoverArtAttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.CoverArtDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessMapAttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessMapDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaAttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.OrganizationAttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.OrganizationDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.PageImageDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.PageListDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.PrimaryTagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.SecondaryTagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.StatusDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.TagAttributesDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.TagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.UnknownEntity
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jsoup.parser.Parser
import java.util.Locale

class NamiComiHelper(lang: String) {

    val filters = NamiComiFilters()

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        prettyPrint = true
        serializersModule += SerializersModule {
            polymorphic(EntityDto::class) {
                subclass(OrganizationDto::class)
                subclass(ChapterDataDto::class)
                subclass(CoverArtDto::class)
                subclass(MangaDataDto::class)
                subclass(PageListDataDto::class)
                subclass(TagDto::class)
                subclass(PrimaryTagDto::class)
                subclass(SecondaryTagDto::class)
                subclass(EntityAccessMapDataDto::class)
                defaultDeserializer { UnknownEntity.serializer() }
            }

            polymorphic(AttributesDto::class) {
                subclass(OrganizationAttributesDto::class)
                subclass(ChapterAttributesDto::class)
                subclass(CoverArtAttributesDto::class)
                subclass(MangaAttributesDto::class)
                subclass(TagAttributesDto::class)
                subclass(EntityAccessMapAttributesDto::class)
            }

            contextual(PageImageDto::class, PageImageDto.serializer())
        }
    }

    val intl = Intl(
        language = lang,
        baseLanguage = NamiComiConstants.english,
        availableLanguages = setOf(NamiComiConstants.english),
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = { lang -> Intl.createDefaultMessageFileName(lang) },
    )

    /**
     * Gets the ID from the url
     */
    fun getIdFromUrl(url: String) = url.substringAfterLast("/")

    /**
     * Check if the manga url contains a valid id
     */
    fun containsId(url: String) = getIdFromUrl(url).run { isNotEmpty() }

    /**
     * Get the manga offset pages are 1 based, so subtract 1
     */
    fun getMangaListOffset(page: Int): String = (NamiComiConstants.mangaLimit * (page - 1)).toString()

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

    private fun getPublicationStatus(mangaDataDto: MangaDataDto): Int {
        return when (mangaDataDto.attributes!!.publicationStatus) {
            StatusDto.ONGOING -> SManga.ONGOING
            StatusDto.CANCELLED -> SManga.CANCELLED
            StatusDto.COMPLETED -> SManga.COMPLETED
            StatusDto.HIATUS -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseDate(dateAsString: String): Long =
        NamiComiConstants.dateFormatter.parse(dateAsString)?.time ?: 0

    companion object {
        val markdownLinksRegex = "\\[([^]]+)\\]\\(([^)]+)\\)".toRegex()
        val markdownItalicBoldRegex = "\\*+\\s*([^\\*]*)\\s*\\*+".toRegex()
        val markdownItalicRegex = "_+\\s*([^_]*)\\s*_+".toRegex()

        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()

        val trailingHyphenRegex = "-+$".toRegex()
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
        url = "/title/${mangaDataDto.id}"

        val titleMap = mangaDataDto.attributes!!.title
        title = (titleMap[lang] ?: titleMap.values.first())
            .removeEntities()

        coverFileName?.let {
            thumbnail_url = when (!coverSuffix.isNullOrEmpty()) {
                true -> "${NamiComiConstants.cdnUrl}/covers/${mangaDataDto.id}/$coverFileName$coverSuffix"
                else -> "${NamiComiConstants.cdnUrl}/covers/${mangaDataDto.id}/$coverFileName"
            }
        }
    }

    /**
     * Create an [SManga] from the JSON element with all attributes filled.
     */
    fun createManga(
        mangaDataDto: MangaDataDto,
        lang: String,
        coverSuffix: String?,
    ): SManga {
        val attr = mangaDataDto.attributes!!

        // Things that will go with the genre tags but aren't actually genre
        val extLocale = Locale.forLanguageTag(lang)

        val nonGenres = listOfNotNull(
            attr.contentRating
                .takeIf { it != ContentRatingDto.SAFE }
                ?.let { intl.format("content_rating_genre", intl["content_rating_${it.name.lowercase()}"]) },
            attr.originalLanguage
                ?.let { Locale.forLanguageTag(it) }
                ?.getDisplayName(extLocale)
                ?.replaceFirstChar { it.uppercase(extLocale) },
        )

        val organization = mangaDataDto.relationships
            .filterIsInstance<OrganizationDto>()
            .mapNotNull { it.attributes?.name }
            .distinct()

        val coverFileName = mangaDataDto.relationships
            .filterIsInstance<CoverArtDto>()
            .firstOrNull()
            ?.attributes?.fileName

        val tags = filters.getTags(intl).associate { it.id to it.name }

        val genresMap = mangaDataDto.relationships
            .filterIsInstance<AbstractTagDto>()
            .groupBy({ it.attributes!!.group }) { tagDto -> tags[tagDto.id] }
            .mapValues { it.value.filterNotNull().sortedWith(intl.collator) }

        val genreList = NamiComiConstants.tagGroupsOrder.flatMap { genresMap[it].orEmpty() } + nonGenres

        val desc = (attr.description[lang] ?: attr.description["en"])
            ?.removeEntitiesAndMarkdown()
            .orEmpty()

        return createBasicManga(mangaDataDto, coverFileName, coverSuffix, lang).apply {
            description = desc
            author = organization.joinToString()
            status = getPublicationStatus(mangaDataDto)
            genre = genreList
                .filter(String::isNotEmpty)
                .joinToString()
            initialized = true
        }
    }

    /**
     * Create the [SChapter] from the JSON element.
     */
    fun createChapter(chapterDataDto: ChapterDataDto, extLang: String): SChapter {
        val attr = chapterDataDto.attributes!!
        val chapterName = mutableListOf<String>()

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

        attr.name?.let {
            if (it.isNotEmpty()) {
                if (chapterName.isNotEmpty()) {
                    chapterName.add("-")
                }
                chapterName.add(it)
            }
        }

        return SChapter.create().apply {
            url = "/$extLang/chapter/${chapterDataDto.id}"
            name = chapterName.joinToString(" ").removeEntities()
            date_upload = parseDate(attr.publishAt)
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
}
