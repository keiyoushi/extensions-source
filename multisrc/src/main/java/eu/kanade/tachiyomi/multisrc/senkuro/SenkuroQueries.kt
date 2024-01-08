package eu.kanade.tachiyomi.multisrc.senkuro

import kotlinx.serialization.Serializable

@Serializable
data class GraphQL<T>(
    val query: String,
    val variables: T,
)

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

@Serializable
data class SearchVariables(
    val query: String? = null,
    val type: FiltersDto? = null,
    val status: FiltersDto? = null,
    val translationStatus: FiltersDto? = null,
    val genre: FiltersDto? = null,
    val tag: FiltersDto? = null,
    val format: FiltersDto? = null,
    val rating: FiltersDto? = null,
    val offset: Int? = null,
) {
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
            %translationStatus: MangaTachiyomiSearchTranslationStatusFilter,
            %genre: MangaTachiyomiSearchGenreFilter,
            %tag: MangaTachiyomiSearchTagFilter,
            %format: MangaTachiyomiSearchGenreFilter,
            %rating: MangaTachiyomiSearchTagFilter,
            %offset: Int,
        ) {
            mangaTachiyomiSearch(
                query:%query,
                type: %type,
                status: %status,
                translationStatus: %translationStatus,
                genre: %genre,
                tag: %tag,
                format: %format,
                rating: %rating,
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
                genres {
                    slug
                    titles {
                        lang
                        content
                    }
                }
                tags {
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
        val genres: List<FilterDataDto>,
        val tags: List<FilterDataDto>,
    ) {
        @Serializable
        data class FilterDataDto(
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
                genres {
                    id
                    slug
                    titles {
                        lang
                        content
                    }
                }
                tags {
                    id
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
