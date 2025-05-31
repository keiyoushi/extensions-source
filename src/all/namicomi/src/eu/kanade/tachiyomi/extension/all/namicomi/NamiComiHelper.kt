package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.extension.all.namicomi.dto.AbstractTagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.CoverArtDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.OrganizationDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.StatusDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.UnknownEntity
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import java.util.Locale

class NamiComiHelper(lang: String) {

    val filters = NamiComiFilters()

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        serializersModule += SerializersModule {
            polymorphic(EntityDto::class) {
                defaultDeserializer { UnknownEntity.serializer() }
            }
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
     * Get the manga offset pages are 1 based, so subtract 1
     */
    fun getMangaListOffset(page: Int): String = (NamiComiConstants.mangaLimit * (page - 1)).toString()

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
            .orEmpty()

        return SManga.create().apply {
            initialized = true
            url = mangaDataDto.id
            description = desc
            author = organization.joinToString()
            status = getPublicationStatus(mangaDataDto)
            genre = genreList
                .filter(String::isNotEmpty)
                .joinToString()

            mangaDataDto.attributes.title.let { titleMap ->
                title = titleMap[lang] ?: titleMap.values.first()
            }

            coverFileName?.let {
                thumbnail_url = when (!coverSuffix.isNullOrEmpty()) {
                    true -> "${NamiComiConstants.cdnUrl}/covers/${mangaDataDto.id}/$coverFileName$coverSuffix"
                    else -> "${NamiComiConstants.cdnUrl}/covers/${mangaDataDto.id}/$coverFileName"
                }
            }
        }
    }

    /**
     * Create the [SChapter] from the JSON element.
     */
    fun createChapter(chapterDataDto: ChapterDataDto): SChapter {
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
            url = chapterDataDto.id
            name = chapterName.joinToString(" ")
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

    companion object {
        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val trailingHyphenRegex = "-+$".toRegex()
    }
}
