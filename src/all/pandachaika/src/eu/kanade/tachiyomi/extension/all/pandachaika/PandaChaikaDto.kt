package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
fun filterTags(include: String = "", exclude: List<String> = emptyList(), tags: List<String>): String? {
    return tags.filter { it.startsWith("$include:") && exclude.none { substring -> it.startsWith("$substring:") } }
        .joinToString {
            it.substringAfter(":").replace("_", " ").split(" ").joinToString(" ") { s ->
                s.replaceFirstChar { sr ->
                    if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
                }
            }
        }.takeIf { it.isNotBlank() }
}
fun getReadableSize(bytes: Double): String {
    return when {
        bytes >= 300 * 1000 * 1000 -> "${"%.2f".format(bytes / (1000.0 * 1000.0 * 1000.0))} GB"
        bytes >= 100 * 1000 -> "${"%.2f".format(bytes / (1000.0 * 1000.0))} MB"
        bytes >= 1000 -> "${"%.2f".format(bytes / (1000.0))} kB"
        else -> "$bytes B"
    }
}

@Serializable
class Archive(
    val download: String,
    val posted: Long,
    val title: String,
)

@Serializable
class LongArchive(
    private val thumbnail: String,
    private val title: String,
    val id: Int,
    private val posted: Long?,
    private val public_date: Long?,
    private val filecount: Int,
    private val filesize: Double,
    private val tags: List<String>,
    private val title_jpn: String?,
    private val uploader: String,
) {
    fun toSManga() = SManga.create().apply {
        val groups = filterTags("group", tags = tags)
        val artists = filterTags("artist", tags = tags)
        val publishers = filterTags("publisher", tags = tags)
        val characters = filterTags("character", tags = tags)
        val male = filterTags("male", tags = tags)
        val female = filterTags("female", tags = tags)
        val others = filterTags(exclude = listOf("female", "male", "artist", "publisher", "group", "parody"), tags = tags)
        val parodies = filterTags("parody", tags = tags)
        var appended = false

        url = id.toString()
        title = this@LongArchive.title
        thumbnail_url = thumbnail
        author = groups ?: artists
        artist = artists
        genre = listOf(male, female, others).joinToString()
        description = buildString {
            append("Uploader: ", uploader.ifEmpty { "Anonymous" }, "\n")
            publishers?.let {
                append("Publishers: ", it, "\n")
            }
            append("\n")

            parodies?.let {
                append("Parodies: ", it, "\n")
                appended = true
            }
            characters?.let {
                append("Characters: ", it, "\n")
                appended = true
            }
            if (appended) append("\n")

            male?.let {
                append("Male tags: ", it, "\n\n")
            }
            female?.let {
                append("Female tags: ", it, "\n\n")
            }
            others?.let {
                append("Other tags: ", it, "\n\n")
            }

            title_jpn?.takeIf { it.isNotEmpty() }?.let { append("Japanese Title: ", it, "\n") }
            append("Pages: ", filecount, "\n")
            append("File Size: ", getReadableSize(filesize), "\n")

            try {
                append("Public Date: ", dateReformat.format(Date(public_date!! * 1000)), "\n")
            } catch (_: Exception) {}
            try {
                append("Posted: ", dateReformat.format(Date(posted!! * 1000)), "\n")
            } catch (_: Exception) {}
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }
}

@Serializable
class ArchiveResponse(
    val archives: List<LongArchive>,
    val has_next: Boolean,
)
