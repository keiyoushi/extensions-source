package eu.kanade.tachiyomi.extension.en.lgbtics

import eu.kanade.tachiyomi.source.model.Filter

internal class LgbticsSortFilter(name: String, private val options: List<Pair<String, String>>, initialState: Int) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), initialState) {
    fun key() = options[state].second
}

internal class LgbticsGenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

internal class LgbticsGenreFilter(name: String, tags: List<TagDto>) : Filter.Group<LgbticsGenreCheckBox>(name, tags.map { LgbticsGenreCheckBox(it.name, it.slug) })

internal class LgbticsStatusTag(name: String, val slug: String) : Filter.CheckBox(name)

internal class LgbticsStatusFilter(name: String) :
    Filter.Group<LgbticsStatusTag>(
        name,
        listOf(
            LgbticsStatusTag("Ongoing", "on-going"),
            LgbticsStatusTag("Completed", "end"),
            LgbticsStatusTag("Canceled", "canceled"),
            LgbticsStatusTag("On Hold", "on-hold"),
            LgbticsStatusTag("Upcoming", "upcoming"),
        ),
    )

internal class LgbticsAuthorFilter : Filter.Text("Characters")

internal class LgbticsArtistFilter : Filter.Text("Artists")

internal class LgbticsParodyFilter(name: String, private val tags: List<TagDto>) : Filter.Select<String>(name, (listOf("Any") + tags.map { it.name }).toTypedArray()) {
    fun slug() = tags.getOrNull(state - 1)?.slug
}
