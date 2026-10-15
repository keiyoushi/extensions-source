package eu.kanade.tachiyomi.extension.all.leagueoflegends

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LOLHub(
    private val sections: LOLSections,
) : Iterable<LOLComic> by sections

@Serializable
class LOLSections(
    private val series: LOLData,
    @SerialName("one-shots")
    private val oneShots: LOLData,
) : Iterable<LOLComic> {
    override fun iterator() = (series + oneShots).iterator()
}

@Serializable
class LOLData(
    private val data: List<LOLComic>,
) : Iterable<LOLComic> by data

@Serializable
class LOLComic(
    val title: String? = null,
    val subtitle: String? = null,
    val index: Float? = null,
    private val url: String? = null,
    val description: String? = null,
    val background: LOLImage? = null,
    @SerialName("featured-champions")
    val champions: List<LOLChampion>? = null,
) {
    override fun toString() = url?.substringAfter("/comic/") ?: error("Empty URL")
}

@Serializable
class LOLIssues(
    private val issues: List<LOLComic>,
) : Iterable<LOLComic> by issues.reversed()

@Serializable
class LOLPages(
    @SerialName("staging-date")
    val date: String,
    @SerialName("desktop-pages")
    private val pages: List<List<LOLImage>>,
) : Iterable<LOLImage> {
    override fun iterator() = pages.flatten().iterator()
}

@Serializable
class LOLImage(private val uri: String) {
    override fun toString() = uri
}

@Serializable
class LOLChampion(private val name: String) {
    override fun toString() = name
}
