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
                        extraInfo
        				urlCoverOri
        				urlPath
                        max_chapterNode {
                            data {
                                imageFile {
                                    urlList
                                }
                            }
                        }
                        first_chapterNode {
                            data {
                                imageFile {
                                    urlList
                                }
                            }
                        }
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
                    extraInfo
                    urlCoverOri
                    urlPath
                    max_chapterNode {
                        data {
                            imageFile {
                                urlList
                            }
                        }
                    }
                    first_chapterNode {
                        data {
                            imageFile {
                                urlList
                            }
                        }
                    }
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
                    srcTitle
                    userNode {
                        data {
                            name
                        }
                    }
                    dupChapters {
                        data {
                            id
                            dname
                            title
                            dateModify
                            dateCreate
                            urlPath
                            srcTitle
                            userNode {
                                data {
                                    name
                                }
                            }
                        }
                    }
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
