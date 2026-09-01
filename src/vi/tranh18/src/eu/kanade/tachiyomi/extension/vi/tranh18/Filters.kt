package eu.kanade.tachiyomi.extension.vi.tranh18

import eu.kanade.tachiyomi.source.model.Filter

class Genre(val name: String, val genre: String) {
    override fun toString() = name
}

class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Thể loại", genres)

class StatusList(status: Array<Genre>) : Filter.Select<Genre>("Tiến độ", status)

class TagList(tags: Array<Genre>) : Filter.Select<Genre>("Từ khóa", tags)
