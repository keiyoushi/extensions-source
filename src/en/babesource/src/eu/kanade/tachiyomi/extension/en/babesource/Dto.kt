package eu.kanade.tachiyomi.extension.en.babesource

import kotlinx.serialization.Serializable

@Serializable
class FilterData(val categories: List<ListOption>, val paysites: List<ListOption>, val tags: List<ListOption>)

@Serializable
class ListOption(val name: String, val path: String)
