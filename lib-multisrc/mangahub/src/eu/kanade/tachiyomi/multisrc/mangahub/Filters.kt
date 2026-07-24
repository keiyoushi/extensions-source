package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.source.model.Filter

class Genre(title: String, val key: String) : Filter.CheckBox(title) {
    override fun toString(): String = name
}

class Order(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
    val included: List<String>
        get() = state.filter { it.state }.map { it.key }
}
