package eu.kanade.tachiyomi.extension.all.batotov4

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .replace("%", "$")
        .replace(whitespace, " ")
}

private val whitespace = Regex("\\s+")

private const val COMIC_NODE = """
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
        urlCoverOri
    }
"""

// Query for comic search
val COMIC_SEARCH_QUERY: String = buildQuery {
    """
        query (%select: Comic_Browse_Select) {
            get_comic_browse(select: %select) {
                paging {
                    next
                }
                items {
                    $COMIC_NODE
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
                $COMIC_NODE
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
                    comicId
                    id
                    serial
                    dname
                    title
                    dateCreate
                    dateModify
                    userNode {
                        data {
                            name
                        }
                    }
                    groupNodes {
                        data {
                            name
                        }
                    }
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
                    id,
                    comicId,
                    imageFile {
                        urlList
                    }
                }
            }
        }
    """
}

// Query for my updates
val SSER_MY_UPDATES_QUERY: String = buildQuery {
    """
        query (%select: Sser_MyUpdates_Select) {
            get_sser_myUpdates(select: %select) {
                paging {
                    next
                }
                items {
                    $COMIC_NODE
                }
            }
        }
    """
}

// Query for my history
val SSER_MY_HISTORY_QUERY: String = buildQuery {
    """
        query (%select: Sser_MyHistory_Select) {
            get_sser_myHistory(select: %select) {
                reqLimit
                newStart
                items {
                    comicNode {
                        $COMIC_NODE
                    }
                }
            }
        }
    """
}

// Query for user's publish comic list
val USER_COMIC_LIST_QUERY: String = buildQuery {
    """
        query (%select: User_ComicList_Select) {
            get_user_comicList(select: %select) {
                paging {
                    next
                }
                items {
                    $COMIC_NODE
                }
            }
        }
    """
}
