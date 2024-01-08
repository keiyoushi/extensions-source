package eu.kanade.tachiyomi.extension.all.mangapark

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

val SEARCH_QUERY = buildQuery {
    """
        query (
            %select: SearchComic_Select
        ) {
        	get_searchComic(
        		select: %select
        	) {
        		items {
        			data {
        				id
        				name
        				altNames
        				artists
        				authors
        				genres
        				originalStatus
                        uploadStatus
        				summary
        				urlCoverOri
        				urlPath
        			}
        		}
        	}
        }
    """
}

val DETAILS_QUERY = buildQuery {
    """
        query(
            %id: ID!
        ) {
            get_comicNode(
                id: %id
            ) {
                data {
                    id
                    name
                    altNames
                    artists
                    authors
                    genres
                    originalStatus
                    uploadStatus
                    summary
                    urlCoverOri
                    urlPath
                }
            }
        }
    """
}

val CHAPTERS_QUERY = buildQuery {
    """
        query(
            %id: ID!
        ) {
            get_comicChapterList(
                comicId: %id
            ) {
                data {
                    id
                    dname
                    title
                    dateModify
                    dateCreate
                    urlPath
                }
            }
        }
    """
}

val PAGES_QUERY = buildQuery {
    """
        query(
            %id: ID!
        ) {
            get_chapterNode(
            	id: %id
            ) {
                data {
                    imageFile {
                        urlList
                    }
                }
            }
        }
    """
}
