package eu.kanade.tachiyomi.multisrc.zingtheme

import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Response

/** API endpoint, nonce and browser origin obtained from the same HTML response. */
class ApiConfig(val origin: HttpUrl, val ajaxUrl: HttpUrl, val nonce: String) {
    fun pageUrl(path: String): String {
        val page = origin.resolve(path)!!
        return origin.newBuilder().encodedPath(page.encodedPath).encodedQuery(page.encodedQuery).build().toString()
    }

    fun headers(headers: Headers, path: String): Headers = headers.newBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .set("Referer", pageUrl(path))
        .set("Origin", origin.toString().removeSuffix("/"))
        .build()
}

fun Response.apiConfig(): ApiConfig = use { parseApiConfig(it.body.string(), it.request.url) }

fun parseApiConfig(html: String, url: HttpUrl): ApiConfig {
    val config = CONFIG_REGEX.find(html)?.groupValues?.get(1)?.parseAs<ConfigDto>()
        ?: error("Could not find the site's API configuration")
    val origin = url.resolve("/")!!
    val ajaxUrl = requireNotNull(origin.resolve(config.ajaxUrl)) { "Invalid API URL" }
    require(ajaxUrl.resolve("/") == origin) { "API configuration belongs to another origin" }
    require(config.nonce.isNotBlank()) { "The site's API nonce is empty" }
    return ApiConfig(origin, ajaxUrl, config.nonce)
}

@Serializable
private class ConfigDto(@SerialName("ajax_url") val ajaxUrl: String, val nonce: String)

private val CONFIG_REGEX = Regex("""(?:var|let|const)\s+zing\s*=\s*(\{.*?\})""")
