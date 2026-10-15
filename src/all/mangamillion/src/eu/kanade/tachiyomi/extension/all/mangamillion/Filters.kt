package eu.kanade.tachiyomi.extension.all.mangamillion

import eu.kanade.tachiyomi.source.model.Filter

class TagCheckBox(val id: Int, name: String) : Filter.CheckBox(name)

open class TagGroup(name: String, tags: List<Tag>) : Filter.Group<TagCheckBox>(name, tags.map { TagCheckBox(it.id, it.name) }) {
    val checkedIds: List<Int> get() = state.filter(TagCheckBox::state).map(TagCheckBox::id)
}

open class TagIdGroup(name: String, tags: List<Tag>) : TagGroup(name, tags)

class GenreFilter(tags: List<Tag>) : TagIdGroup("Genre", tags)

class ThemeFilter(tags: List<Tag>) : TagIdGroup("Theme", tags)

class HighlightsFilter(tags: List<Tag>) : TagIdGroup("Highlights", tags)

class RatingFilter(tags: List<Tag>) : TagGroup("Rating", tags)
