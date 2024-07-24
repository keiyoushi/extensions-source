package eu.kanade.tachiyomi.extension.all.ninenineninehentai

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

val POPULAR_QUERY: String = buildQuery {
    """
        query(
            %size: Int
            %language: String
            %dateRange: Int
            %page: Int
        ) {
            queryPopularChapters(
                size: %size
                language: %language
                dateRange: %dateRange
                page: %page
            ) {
                edges {
                    _id
                    name
                    uploadDate
                    format
                    description
                    language
                    pages
                    firstPics
                    tags
                }
            }
        }
    """
}

val SEARCH_QUERY: String = buildQuery {
    """
        query(
            %search: SearchInput
            %size: Int
            %page: Int
        ) {
            queryChapters(
                limit: %size
                search: %search
                page: %page
            ) {
                edges {
                    _id
                    name
                    uploadDate
                    format
                    description
                    language
                    pages
                    firstPics
                    tags
                }
            }
        }
    """
}

val DETAILS_QUERY: String = buildQuery {
    """
        query(
            %id: String
        ) {
            queryChapter(
                chapterId: %id
            ) {
                _id
                name
                uploadDate
                format
                description
                language
                pages
                firstPics
                tags
            }
        }
    """
}

val PAGES_QUERY: String = buildQuery {
    """
        query(
            %id: String
        ) {
            queryChapter(
                chapterId: %id
            ) {
                _id
                pictureUrls {
				    picCdn
				    pics
                    picsM
                }
            }
        }
    """
}
