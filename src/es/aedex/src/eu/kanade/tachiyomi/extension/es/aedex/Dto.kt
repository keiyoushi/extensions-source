package eu.kanade.tachiyomi.extension.es.aedex

import eu.kanade.tachiyomi.multisrc.catharsis.GenreDto
import eu.kanade.tachiyomi.multisrc.catharsis.MangaDto
import kotlinx.serialization.Serializable

@Serializable
internal class Payload(val stream: String)

@Serializable
internal class Listing(val allComics: List<MangaDto>, val initialPagination: Pagination)

@Serializable
internal class Pagination(val page: Int, val totalPages: Int)

@Serializable
internal class Genres(val initialGenres: List<GenreDto>)
