package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable

val SEARCH_QUERY = $$"""
query searchTachiyomiManga(
  $query: String,
  $type: MangaTachiyomiSearchTypeFilter,
  $status: MangaTachiyomiSearchStatusFilter,
  $rating: MangaTachiyomiSearchRatingFilter,
  $format: MangaTachiyomiSearchFormatFilter,
  $translationStatus: MangaTachiyomiSearchTranslationStatusFilter,
  $label: MangaTachiyomiSearchLabelFilter,
  $orderBy: MangaTachiyomiOrder,
  $offset: Int
) {
  mangaTachiyomiSearch(
    query: $query,
    type: $type,
    status: $status,
    rating: $rating,
    format: $format,
    translationStatus: $translationStatus,
    label: $label,
    orderBy: $orderBy,
    offset: $offset
  ) {
    mangas {
      id
      slug
      originalName {
        lang
        content
      }
      titles {
        lang
        content
      }
      alternativeNames {
        lang
        content
      }
      cover {
        original {
          url
        }
      }
    }
  }
}
"""

val FILTERS_QUERY = $$"""
query fetchTachiyomiSearchFilters {
  mangaTachiyomiSearchFilters {
    labels {
      id
      rootId
      slug
      titles {
        lang
        content
      }
    }
  }
}
"""

val DETAILS_QUERY = $$"""
query fetchTachiyomiManga($mangaId: ID!) {
  mangaTachiyomiInfo(mangaId: $mangaId) {
    id
    slug
    originalName {
      lang
      content
    }
    titles {
      lang
      content
    }
    alternativeNames {
      lang
      content
    }
    localizations {
      lang
      description
    }
    type
    rating
    status
    formats
    labels {
      id
      rootId
      slug
      titles {
        lang
        content
      }
    }
    translationStatus
    cover {
      original {
        url
      }
    }
    mainStaff {
      roles
      person {
        name
      }
    }
  }
}
"""

val CHAPTERS_QUERY = $$"""
query fetchTachiyomiChapters(
  $mangaId: ID!
) {
  mangaTachiyomiChapters(
    mangaId: $mangaId
  ) {
    message
    chapters {
      id
      slug
      branchId
      name
      teamIds
      number
      volume
      createdAt
      updatedAt
    }
    teams {
      id
      slug
      name
    }
  }
}
"""

val PAGES_QUERY = $$"""
query fetchTachiyomiChapterPages(
  $mangaId: ID!,
  $chapterId: ID!
) {
  mangaTachiyomiChapterPages(
    mangaId: $mangaId,
    chapterId: $chapterId
  ) {
    pages {
      url
    }
  }
}
"""

@Serializable
class SearchTachiyomiMangaVariables(
    val query: String? = null,
    val type: ExcludeInclude? = null,
    val status: ExcludeInclude? = null,
    val rating: ExcludeInclude? = null,
    val format: ExcludeInclude? = null,
    val translationStatus: ExcludeInclude? = null,
    val label: ExcludeInclude? = null,
    val orderBy: OrderByDto? = null,
    val offset: Int? = null,
)

@Serializable
class ExcludeInclude(
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
) {
    fun isNotEmpty() = include.isNotEmpty() || exclude.isNotEmpty()
}

@Serializable
class OrderByDto(
    val direction: String,
    val field: String,
)

@Serializable
class FetchTachiyomiMangaVariables(
    val mangaId: String,
)

@Serializable
class FetchTachiyomiChaptersVariables(
    val mangaId: String,
)

@Serializable
class FetchTachiyomiChapterPagesVariables(
    val mangaId: String,
    val chapterId: String,
)

@Serializable
object EmptyObject
