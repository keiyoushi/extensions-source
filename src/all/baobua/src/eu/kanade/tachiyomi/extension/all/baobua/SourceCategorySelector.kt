package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SourceCategory(private val name: String, var cat: String) {
    override fun toString() = this.name

    fun buildUrl(baseUrl: String): String {
        return "$baseUrl/".toHttpUrl().newBuilder()
            .addEncodedQueryParameter("cat", this.cat)
            .build()
            .toString()
    }
}

class SourceCategorySelector(
    name: String,
    categories: List<SourceCategory>,
) : Filter.Select<SourceCategory>(name, categories.toTypedArray()) {

    val selectedCategory: SourceCategory?
        get() = if (state > 0) values[state] else null

    companion object {

        fun create(baseUrl: String): SourceCategorySelector {
            val options = listOf(
                SourceCategory("unselected", ""),
                SourceCategory("大胸美女", "YmpydEtkNzV5NHJKcDJYVGtOVW0yZz09"),
                SourceCategory("巨乳美女", "Q09EdlMvMHgweERrUitScTFTaDM4Zz09"),
                SourceCategory("全裸写真", "eXZzejJPNFRVNzJqKzFDUmNzZEU2QT09"),
                SourceCategory("chinese", "bG9LamJsWWdSbGcyY0FEZytldkhTZz09"),
                SourceCategory("chinese models", "OCtTSEI2YzRTcWMvWUsyeDM0aHdzdUIwWDlHMERZUEZaVHUwUEVUVWo3QT0"),
                SourceCategory("korean", "Tm1ydGlaZ1A2YWM3a3BvYWh6L3dIdz09"),
                SourceCategory("korea", "bzRjeWR0akQrRWpxRE1xOGF6TW5Tdz09"),
                SourceCategory("korean models", "TGZTVGtwOCtxTW1TQU1KYWhUb01DQT09"),
                SourceCategory("big boobs", "UmFLQVkvVndGNlpPckwvZkpVaEE4UT09"),
                SourceCategory("adult", "b2RFSnlwdWxyREMxVmRpcThKVXRLUT09"),
                SourceCategory("nude-art", "djFqa293VmFZMEJLdDlUWndsMGtldz09"),
                SourceCategory("Asian adult photo", "SHBGZHFueTVNeUlxVHRLaU53RjU2NS9VcjNxRVg3VnhqTGJoK25YaVQ1UT0"),
                SourceCategory("cosplay", "OEI2c000ZDBxakwydjZIUVJaRnlMQT09"),
                SourceCategory("hot", "c3VRb3RJZ2wrU2tTYmpGSUVqMnFndz09"),
                SourceCategory("big breast", "dkQ3b0RiK0xpZDRlMVNSY3lUNkJXQT09"),
            )

            return SourceCategorySelector("Category", options)
        }
    }
}
