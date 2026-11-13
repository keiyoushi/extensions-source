package eu.kanade.tachiyomi.extension.cs.mangaboomers

import eu.kanade.tachiyomi.source.model.Filter

class GenreCheckBox(name: String, val id: Int) : Filter.CheckBox(name)

class GenreFilter(tags: List<TagDto>) : Filter.Group<GenreCheckBox>("Žánry", tags.map { GenreCheckBox(it.name, it.id) }) {
    val selected: List<Int> get() = state.filter { it.state }.map { it.id }
}

open class OptionFilter(name: String, private val options: List<String>) : Filter.Select<String>(name, (listOf("Vše") + options).toTypedArray()) {
    val selected: String? get() = options.getOrNull(state - 1)
}

class TypeFilter(types: List<OptionDto>) : OptionFilter("Typ", types.map { it.type.trim() }.distinct())

class StateFilter(states: List<OptionDto>) : OptionFilter("Stav překladu", states.map { it.type.trim() }.distinct())
