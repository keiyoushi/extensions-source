package eu.kanade.tachiyomi.extension.all.swarm

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Sort by", arrayOf("Last updated", "Title")),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        builder.addQueryParameter("order", arrayOf("last_updated", "title")[state])
    }
}

class ContentFilter(showAdult: Boolean) :
    Filter.Select<String>("Content", arrayOf("Safe", "All", "18+ only"), if (showAdult) 1 else 0),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        when (state) {
            0 -> builder.addQueryParameter("nsfw", "false").addQueryParameter("suggestive", "false")
            2 -> builder.addQueryParameter("nsfw", "true")
        }
    }
}

class CheckBoxOption(name: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, private val param: String, options: List<String>) :
    Filter.Group<CheckBoxOption>(name, options.map(::CheckBoxOption)),
    UrlFilter {
    open fun values(name: String) = listOf(name)

    override fun addToUrl(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }.flatMap { values(it.name) }.distinct().joinToString(",")
        if (checked.isNotEmpty()) builder.addQueryParameter(param, checked)
    }
}

class GenreFilter(genres: List<String>) : CheckBoxGroup("Genres (any of)", "genres", genres)

class TypeFilter(types: List<String>) : CheckBoxGroup("Types (any of)", "types", types) {
    // Titles carry the type in either case ("Manhwa", "manhwa") and the API matches it exactly
    override fun values(name: String) = listOf(name, name.lowercase())
}

class TextParamFilter(name: String, private val param: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val value = state.split(',').map(String::trim).filter(String::isNotEmpty).joinToString(",")
        if (value.isNotEmpty()) builder.addQueryParameter(param, value)
    }
}
