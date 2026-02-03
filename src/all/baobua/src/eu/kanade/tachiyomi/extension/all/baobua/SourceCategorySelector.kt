package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SourceCategory(private val name: String, var cat: String) {
    override fun toString() = this.name

    fun buildUrl(baseUrl: String, page: Int): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("category")
        .addPathSegment(this.cat)
        .addQueryParameter("page", page.toString())
        .build()
        .toString()
}

class SourceCategorySelector(
    name: String,
    categories: List<SourceCategory>,
) : Filter.Select<SourceCategory>(name, categories.toTypedArray()) {

    val selectedCategory: SourceCategory?
        get() = if (state > 0) values[state] else null

    companion object {

        fun create(): SourceCategorySelector {
            val options = listOf(
                SourceCategory("", ""),
                SourceCategory("Ao-yem", "Ao-yem"),
                SourceCategory("Asia", "Asia"),
                SourceCategory("Beauty", "beauty"),
                SourceCategory("Bikini", "Bikini"),
                SourceCategory("China", "China"),
                SourceCategory("Cosplay", "Cosplay"),
                SourceCategory("Japan", "Japan"),
                SourceCategory("Nude", "Nude"),
                SourceCategory("Sexy", "Sexy"),
                SourceCategory("Top", "Top"),
                SourceCategory("Tattoo", "tattoo"),
                SourceCategory("Vietnam", "Vietnam"),
            )

            return SourceCategorySelector("Category", options)
        }
    }
}
