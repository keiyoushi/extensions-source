package eu.kanade.tachiyomi.multisrc.mangotheme

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface MangoThemeUrlQueryFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class MangoThemeTagFilterOption(
    val name: String,
    val id: String,
)

internal class StatusFilter(
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>("Status", options.map { it.first }.toTypedArray()),
    MangoThemeUrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        options[state].second
            .takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("status_id", it) }
    }
}

internal class FormatFilter(
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>("Formato", options.map { it.first }.toTypedArray()),
    MangoThemeUrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        options[state].second
            .takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("formato_id", it) }
    }
}

internal class MinChaptersFilter :
    Filter.Text("Cap\u00edtulos m\u00ednimos"),
    MangoThemeUrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        state.trim()
            .takeIf { it.toIntOrNull() != null }
            ?.let { url.addQueryParameter("min_capitulos", it) }
    }
}

internal class TagCheckBox(
    name: String,
    val id: String,
) : Filter.CheckBox(name)

internal class TagFilter(
    options: List<MangoThemeTagFilterOption>,
) : Filter.Group<TagCheckBox>(
    "Tags",
    options.map { TagCheckBox(it.name, it.id) },
),
    MangoThemeUrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        state.filter { it.state }
            .joinToString(",") { it.id }
            .takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("tag_ids", it) }
    }
}
