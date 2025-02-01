package eu.kanade.tachiyomi.extension.all.hitomi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class Gallery(
    val galleryurl: String,
    val title: String,
    val japaneseTitle: String?,
    val date: String,
    val type: String?,
    val language: String?,
    val tags: List<Tag>?,
    val artists: List<Artist>?,
    val groups: List<Group>?,
    val characters: List<Character>?,
    val parodys: List<Parody>?,
    val files: List<ImageFile>,
)

@Serializable
class ImageFile(
    val hash: String,
    val haswebp: Int?,
    val hasavif: Int?,
    val hasjxl: Int?,
)

@Serializable
class Tag(
    private val female: JsonPrimitive?,
    private val male: JsonPrimitive?,
    private val tag: String,
) {
    val formatted get() = if (female?.content == "1") {
        tag.toCamelCase() + " ♀"
    } else if (male?.content == "1") {
        tag.toCamelCase() + " ♂"
    } else {
        tag.toCamelCase()
    }
}

@Serializable
class Artist(
    private val artist: String,
) {
    val formatted get() = artist.toCamelCase()
}

@Serializable
class Group(
    private val group: String,
) {
    val formatted get() = group.toCamelCase()
}

@Serializable
class Character(
    private val character: String,
) {
    val formatted get() = character.toCamelCase()
}

@Serializable
class Parody(
    private val parody: String,
) {
    val formatted get() = parody.toCamelCase()
}

private fun String.toCamelCase(): String {
    val result = StringBuilder(length)
    var capitalize = true
    for (char in this) {
        result.append(
            if (capitalize) {
                char.uppercase()
            } else {
                char.lowercase()
            },
        )
        capitalize = char.isWhitespace()
    }
    return result.toString()
}
