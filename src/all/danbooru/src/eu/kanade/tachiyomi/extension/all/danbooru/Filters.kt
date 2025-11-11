package eu.kanade.tachiyomi.extension.all.danbooru

import eu.kanade.tachiyomi.source.model.Filter

internal class FilterTags : Filter.Text("Tags")
internal class FilterDescription : Filter.Text("Description")
internal class FilterIsDeleted : Filter.CheckBox("Deleted")

internal class FilterCategory : Filter.Select<String>("Category", values, 1) {
    companion object {
        val values = arrayOf("", "Series", "Collection")
        val keys = arrayOf("", "series", "collection")
    }

    val selected: String get() = keys[state]
}

internal class FilterOrder : Filter.Sort("Order", values, Selection(0, false)) {
    companion object {
        val values = arrayOf("Last updated", "Name", "Recently created", "Post count")
        val keys = arrayOf("updated_at", "name", "created_at", "post_count")
    }

    val selected: String? get() = state?.let { keys[it.index] }
}

internal fun FilterOrder(key: String?, ascending: Boolean = false) = FilterOrder().apply {
    state = Filter.Sort.Selection(FilterOrder.keys.indexOf(key), ascending)
}
