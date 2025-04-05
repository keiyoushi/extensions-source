package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
enum class MangaFormat(val format: Int) {
    Image(0),
    Archive(1),
    Unknown(2),
    Epub(3),
    Pdf(4),
    ;
    companion object {
        private val map = PersonRole.values().associateBy(PersonRole::role)
        fun fromInt(type: Int) = map[type]
    }
}
enum class PersonRole(val role: Int) {
    Other(1),
    Writer(3),
    Penciller(4),
    Inker(5),
    Colorist(6),
    Letterer(7),
    CoverArtist(8),
    Editor(9),
    Publisher(10),
    Character(11),
    Translator(12),
    ;
    companion object {
        private val map = PersonRole.values().associateBy(PersonRole::role)
        fun fromInt(type: Int) = map[type]
    }
}

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val thumbnail_url: String? = "",
    val localizedName: String? = "",
    val sortName: String? = "",
    val pages: Int,
    val coverImageLocked: Boolean = true,
    val pagesRead: Int,
    val userRating: Float,
    val userReview: String? = "",
    val format: Int,
    val created: String? = "",
    val libraryId: Int,
    val libraryName: String? = "",
)

@Serializable
data class SeriesMetadataDto(
    val id: Int,
    val summary: String? = "",
    val writers: List<Person> = emptyList(),
    val coverArtists: List<Person> = emptyList(),
    val genres: List<Genres> = emptyList(),
    val seriesId: Int,
    val ageRating: Int,
    val publicationStatus: Int,
)

@Serializable
data class Genres(
    val title: String,
)

@Serializable
data class Person(
    val name: String,
)

@Serializable
data class VolumeDto(
    val id: Int,
    val number: Int,
    val name: String,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: String,
    val created: String,
    val seriesId: Int,
    val chapters: List<ChapterDto> = emptyList(),
)

enum class ChapterType {
    Regular, // chapter with volume information
    LooseLeaf, // chapter without volume information
    SingleFileVolume,
    Special,
    ;

    companion object {
        fun of(chapter: ChapterDto, volume: VolumeDto): ChapterType =
            if (volume.number == 100_000) {
                Special
            } else if (volume.number == -100_000) {
                LooseLeaf
            } else {
                if (chapter.number == "-100000") {
                    SingleFileVolume
                } else {
                    Regular
                }
            }
    }
}

@Serializable
data class ChapterDto(
    val id: Int,
    val range: String,
    val number: String,
    val pages: Int,
    val isSpecial: Boolean,
    val title: String,
    val pagesRead: Int,
    val coverImageLocked: Boolean,
    val volumeId: Int,
    val created: String,
    val files: List<FileDto>? = null,
) {
    val fileCount: Int
        get() = files?.size ?: 0
}

@Serializable
data class FileDto(
    val id: Int,
)
