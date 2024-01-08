package eu.kanade.tachiyomi.multisrc.readerfront

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class Work(
    private val name: String,
    val stub: String,
    val thumbnail_path: String,
    val adult: Boolean? = null,
    val type: String? = null,
    val licensed: Boolean? = null,
    val status_name: String? = null,
    val description: String? = null,
    val demographic_name: String? = null,
    val genres: List<NameWrapper>? = null,
    private val people_works: List<PeopleWorks>? = null,
) {
    @Transient
    val authors = people_works?.filter { it.role == 1 }

    @Transient
    val artists = people_works?.filter { it.role == 2 }

    override fun toString() = name
}

@Serializable
data class Release(
    val id: Int,
    val chapter: Int,
    val subchapter: Int,
    val volume: Int,
    private val name: String,
    private val releaseDate: String,
) {
    @Transient
    val number = "$chapter.$subchapter".toFloat()

    @Transient
    val timestamp = dateFormat.parse(releaseDate)?.time ?: 0L

    override fun toString() = buildString {
        if (number > 0) {
            if (volume > 0) append("Volume $volume ")
            append("Chapter ${decimalFormat.format(number)}")
            if (name.isNotEmpty()) append(": ")
        }
        append(name)
    }

    companion object {
        private const val ISO_DATE = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        private val decimalFormat = DecimalFormat("#.##")

        private val dateFormat = SimpleDateFormat(ISO_DATE, Locale.ROOT)
    }
}

@Serializable
data class Chapter(
    private val uniqid: String,
    private val work: UniqidWrapper,
    private val pages: List<Page>,
) : Iterable<Page> by pages {
    /** Get the path of a page in the list. */
    fun path(page: Page) = "/works/$work/$this/$page"

    override fun toString() = uniqid
}

@Serializable
data class Page(private val filename: String, val width: Int) {
    override fun toString() = filename
}

@Serializable
data class UniqidWrapper(private val uniqid: String) {
    override fun toString() = uniqid
}

@Serializable
data class PeopleWorks(val role: Int, private val people: NameWrapper) {
    override fun toString() = people.toString()
}

@Serializable
data class NameWrapper(private val name: String) {
    override fun toString() = name
}
