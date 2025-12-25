package eu.kanade.tachiyomi.extension.all.batotov4

import eu.kanade.tachiyomi.extension.all.batotov4.BatoToV4.Companion.whitespace

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
        .replace(whitespace, " ")
}

// Query for comic search
val COMIC_SEARCH_QUERY: String = buildQuery {
    """
        query (%select: Comic_Browse_Select) {
            get_comic_browse(select: %select) {
                paging {
                    pages
                    page
                    next
                }
                items {
                    data {
                        id
                        name
                        urlPath
                        urlCover300
                        urlCover600
                        urlCover900
                        urlCoverOri
                    }
                }
            }
        }
    """
}

// Query for manga details
val COMIC_NODE_QUERY: String = buildQuery {
    """
        query get_comicNode(%id: ID!) {
            get_comicNode(id: %id) {
                data {
                    id
                    name
                    altNames
                    authors
                    artists
                    originalStatus
                    uploadStatus
                    genres
                    summary
                    extraInfo
                    urlPath
                    urlCover300
                    urlCover600
                    urlCover900
                    urlCoverOri
                }
            }
        }
    """
}

// Query for chapter list
val CHAPTER_LIST_QUERY: String = buildQuery {
    """
        query get_comic_chapterList(%comicId: ID!, %start: Int) {
            get_comic_chapterList(comicId: %comicId, start: %start) {
                data {
                    id
                    dname
                    title
                    urlPath
                    dateCreate
                    dateModify
                }
            }
        }
    """
}

// Query for chapter images
val CHAPTER_NODE_QUERY: String = buildQuery {
    """
        query get_chapterNode(%id: ID!) {
            get_chapterNode(id: %id) {
                data {
                    imageFile {
                        urlList
                    }
                }
            }
        }
    """
}
