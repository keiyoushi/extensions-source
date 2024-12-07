package eu.kanade.tachiyomi.extension.ja.kadocomi

import kotlinx.serialization.Serializable

@Serializable
class KadoComiWorkDto(
    val work: KadoComiWork,
    val latestEpisodes: KadoComiEpisodesResult?,
)

@Serializable
class KadoComiSearchResultsDto(
    val result: List<KadoComiWork> = emptyList(),
)

@Serializable
class KadoComiViewerDto(
    val manuscripts: List<KadoComiManuscript> = emptyList(),
)

@Serializable
class KadoComiWork(
    val code: String = "",
    val id: String = "",
    val thumbnail: String = "",
    val bookCover: String?,
    val title: String = "",
    val serializationStatus: String = "",
    val summary: String? = "",
    val genre: KadoComiTag?,
    val subGenre: KadoComiTag?,
    val tags: List<KadoComiTag>? = emptyList(),
    val authors: List<KadoComiAuthor>? = emptyList(),
)

@Serializable
class KadoComiTag(
    val name: String = "",
)

@Serializable
class KadoComiAuthor(
    val name: String = "",
    val role: String = "",
)

@Serializable
class KadoComiEpisodesResult(
    val result: List<KadoComiEpisode> = emptyList(),
)

@Serializable
class KadoComiEpisode(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val updateDate: String = "",
    val isActive: Boolean = false,
    val internal: KadoComiEpisodeInternalInfo,
)

@Serializable
class KadoComiEpisodeInternalInfo(
    val episodeNo: Int = 1,
)

@Serializable
class KadoComiManuscript(
    val drmHash: String = "",
    val drmImageUrl: String = "",
)
