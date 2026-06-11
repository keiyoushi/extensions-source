package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable

@Serializable
class FetchMangasVariables(
    val after: String? = null,
    val bookmark: ExcludeInclude = ExcludeInclude(),
    val chapters: EmptyObject = EmptyObject,
    val format: ExcludeInclude = ExcludeInclude(),
    val label: ExcludeInclude = ExcludeInclude(),
    val orderDirection: String = "DESC",
    val orderField: String = "POPULARITY_SCORE",
    val originCountry: ExcludeInclude = ExcludeInclude(),
    val rating: ExcludeInclude = ExcludeInclude(),
    val releasedOn: EmptyObject = EmptyObject,
    val search: String? = null,
    val source: ExcludeInclude = ExcludeInclude(),
    val status: ExcludeInclude = ExcludeInclude(),
    val translitionStatus: ExcludeInclude = ExcludeInclude(),
    val type: ExcludeInclude = ExcludeInclude(),
)

@Serializable
object EmptyObject

@Serializable
class ExcludeInclude(
    val exclude: List<String> = emptyList(),
    val include: List<String> = emptyList(),
)

@Serializable
class FetchMangaVariables(
    val slug: String,
)

@Serializable
class FetchMangaChaptersVariables(
    val after: String? = null,
    val branchId: String,
    val number: String? = null,
    val orderBy: OrderByDto = OrderByDto("DESC", "NUMBER"),
)

@Serializable
class OrderByDto(
    val direction: String,
    val field: String,
)

@Serializable
class FetchMangaChapterVariables(
    val slug: String,
    val cdnQuality: String = "auto",
)
