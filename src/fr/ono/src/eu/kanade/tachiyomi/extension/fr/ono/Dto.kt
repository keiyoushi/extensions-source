package eu.kanade.tachiyomi.extension.fr.ono

import kotlinx.serialization.Serializable

@Serializable
class RankingData(
    val getCatalogRanking: RankingPayload? = null,
)

@Serializable
class RankingPayload(
    val series: List<RankingSeries> = emptyList(),
)

@Serializable
class RankingSeries(
    val id: String,
    val slug: String,
    val title: String,
    val contentType: String,
    val imageURL: String? = null,
)

@Serializable
class SeriesDetail(
    val id: String,
    val slug: String,
    val title: String,
    val contentType: String,
    val seriesElements: List<SeriesElement> = emptyList(),
    val summary: String? = null,
    val punchline: String? = null,
    val publicationStatus: String? = null,
    val cover: String? = null,
    val contributors: List<Contributor> = emptyList(),
    val genres: List<Label> = emptyList(),
    val tags: List<Label> = emptyList(),
)

@Serializable
class Contributor(val name: String)

@Serializable
class Label(val label: String)

@Serializable
class SeriesElement(
    val id: String,
    val num: String,
    val title: String? = null,
    val price: String? = null,
    val publishable: Boolean? = null,
    val isBought: Boolean? = null,
    val waitAndRead: WaitAndRead? = null,
)

@Serializable
class WaitAndRead(val __typename: String)

@Serializable
class SearchCatalogData(
    val searchCatalogByTerm: SearchCatalogResult? = null,
)

@Serializable
class SearchCatalogResult(
    val series: List<SearchSeries> = emptyList(),
)

@Serializable
class SearchSeries(
    val id: String,
    val title: String,
    val slug: String,
    val contentType: String,
)

@Serializable
class StartReadingSessionData(
    val startReadingSessionBySlugAndNum: ReadingSessionPayload? = null,
)

@Serializable
class ReadingSessionPayload(
    val __typename: String,
    val unavailabilityReason: String? = null,
    val code: String? = null,
    val publicationMetadata: PublicationMetadata? = null,
    val publicationAccessMethods: List<AccessMethod> = emptyList(),
)

@Serializable
class AccessMethod(
    val __typename: String,
    val publicationId: String? = null,
    val waitAndReadReloadDelay: Long? = null,
)

@Serializable
class UnlockData(
    val unlockPublicationByWnR: UnlockResult? = null,
)

@Serializable
class UnlockResult(
    val success: Boolean? = null,
    val __typename: String? = null,
    val code: String? = null,
)

@Serializable
class PublicationMetadata(
    val pages: List<String> = emptyList(),
)
