package eu.kanade.tachiyomi.multisrc.makaru

import eu.kanade.tachiyomi.source.model.Filter

class LabelFilter(name: String, labels: List<Label>) : Filter.Group<Label>(name, labels)

class Label(name: String) : Filter.CheckBox(name)

fun getStatusList() = listOf(
    Label("Ongoing"),
    Label("Completed"),
)

fun getTypeList() = listOf(
    Label("Manga"),
    Label("Manhwa"),
    Label("Manhua"),
)

fun getGenreList() = listOf(
    Label("Action"),
    Label("Adventure"),
    Label("Comedy"),
    Label("Drama"),
    Label("Fantasy"),
    Label("Horror"),
    Label("Mystery"),
    Label("Romance"),
    Label("Sci-Fi"),
    Label("Slice of Life"),
    Label("Sports"),
    Label("Supernatural"),
    Label("Suspense"),
    Label("Ecchi"),
    Label("Detective"),
    Label("Educational"),
    Label("Harem"),
    Label("Game"),
    Label("Historical"),
    Label("Isekai"),
    Label("Mahou Shoujo"),
    Label("Martial Arts"),
    Label("Mecha"),
    Label("Medical"),
    Label("Memoir"),
    Label("Military"),
    Label("Music"),
    Label("Mythology"),
    Label("Parody"),
    Label("Psychological"),
    Label("Racing"),
    Label("Reincarnation"),
    Label("Samurai"),
    Label("School"),
    Label("Strategy"),
    Label("Super Power"),
    Label("Survival"),
    Label("Time Travel"),
    Label("Vampire"),
    Label("Villain"),
    Label("Workplace"),
    Label("Josei"),
    Label("Kids"),
    Label("Seinen"),
    Label("Shoujo"),
    Label("Shounen"),
    Label("Project"),
    Label("Mirror"),
)
