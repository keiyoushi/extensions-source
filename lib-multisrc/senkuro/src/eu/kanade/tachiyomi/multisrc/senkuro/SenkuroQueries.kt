package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable

@Serializable
data class GraphQL<T>(
    val query: String,
    val variables: T,
)

private fun buildQuery(queryAction: () -> String): String = queryAction()
    .trimIndent()
    .replace("%", "$")

@Serializable
data class SearchVariables(
    val query: String? = null,
    val type: FiltersDto? = null,
    val status: FiltersDto? = null,
    val rating: FiltersDto? = null,
    val format: FiltersDto? = null,
    val translationStatus: FiltersDto? = null,
    val label: FiltersDto? = null,
    val orderBy: OrderDto? = null,
    val offset: Int? = null,
) {
    @Serializable
    data class OrderDto(
        val field: String? = null,
        val direction: String? = null,
    )

    @Serializable
    data class FiltersDto(
        val include: List<String>? = null,
        val exclude: List<String>? = null,
    )
}

val SEARCH_QUERY: String = buildQuery {
    """
        query searchTachiyomiManga(
            %query: String,
            %type: MangaTachiyomiSearchTypeFilter,
            %status: MangaTachiyomiSearchStatusFilter,
            %rating: MangaTachiyomiSearchRatingFilter,
            %format: MangaTachiyomiSearchFormatFilter,
            %translationStatus: MangaTachiyomiSearchTranslationStatusFilter,
            %label: MangaTachiyomiSearchLabelFilter,
            %orderBy: MangaTachiyomiOrder,
            %offset: Int,
        ) {
            mangaTachiyomiSearch(
                query:%query,
                type: %type,
                status: %status,
                rating: %rating,
                format: %format,
                translationStatus: %translationStatus,
                label: %label,
                orderBy: %orderBy,
                offset: %offset,
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
}

@Serializable
data class FetchDetailsVariables(
    val mangaId: String? = null,
)

val DETAILS_QUERY: String = buildQuery {
    """
        query fetchTachiyomiManga(%mangaId: ID!) {
            mangaTachiyomiInfo(mangaId: %mangaId) {
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
}

val CHAPTERS_QUERY: String = buildQuery {
    """
        query fetchTachiyomiChapters(%mangaId: ID!) {
            mangaTachiyomiChapters(mangaId: %mangaId) {
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
                }
                teams {
                    id
                    slug
                    name
                }
            }
        }

    """
}

@Serializable
data class FetchChapterPagesVariables(
    val mangaId: String? = null,
    val chapterId: String? = null,
)

val CHAPTERS_PAGES_QUERY: String = buildQuery {
    """
        query fetchTachiyomiChapterPages(
             %mangaId: ID!,
             %chapterId: ID!
        ) {
            mangaTachiyomiChapterPages(
                mangaId: %mangaId,
                chapterId: %chapterId
            ) {
                pages {
                    url
                }
            }
        }
    """
}

@Serializable
data class MangaTachiyomiSearchFilters(
    val mangaTachiyomiSearchFilters: FilterDto,
) {
    @Serializable
    data class FilterDto(
        val labels: List<FilterDataDto>,
    ) {
        @Serializable
        data class FilterDataDto(
            val id: String,
            val rootId: String,
            val slug: String,
            val titles: List<TitleDto>,
        ) {
            @Serializable
            data class TitleDto(
                val lang: String,
                val content: String,
            )
        }
    }
}

val FILTERS_QUERY: String = buildQuery {
    """
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
}
