package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request

/** Documentation of unused APIs originally used in `zh.qiximh`. */
object Api {

    fun getRankRequest(baseUrl: String, headers: Headers, page: Int, type: Int) =
        getListingRequest("$baseUrl/rankdata.php", headers, page, type)

    fun getSortRequest(baseUrl: String, headers: Headers, page: Int, type: Int) =
        getListingRequest("$baseUrl/sortdata.php", headers, page, type)

    /** @param page 1-5. Website allows 1-10 and contains more items per page. */
    fun getListingRequest(url: String, headers: Headers, page: Int, type: Int): Request {
        val body = FormBody.Builder()
            .add("page_num", page.toString())
            .add("type", type.toString())
            .build()
        return POST(url, headers, body)
    }

    fun getSearchRequest(baseUrl: String, headers: Headers, query: String): Request {
        val body = FormBody.Builder()
            .add("keyword", query)
            .build()
        return POST("$baseUrl/search.php", headers, body)
    }
}
