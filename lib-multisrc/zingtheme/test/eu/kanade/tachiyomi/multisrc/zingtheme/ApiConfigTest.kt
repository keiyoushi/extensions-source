package eu.kanade.tachiyomi.multisrc.zingtheme

import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class ApiConfigTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setupJson() {
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun redirectedConfigurationUsesTheFinalDeployment() {
        val response = Response.Builder()
            .request(Request.Builder().url("https://b.example/").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("const zing = {\"ajax_url\":\"https://b.example/wp-admin/admin-ajax.php\",\"nonce\":\"new-nonce\"}".toResponseBody())
            .priorResponse(Response.Builder().request(Request.Builder().url("https://a.example/").build()).protocol(Protocol.HTTP_1_1).code(301).message("Moved").build())
            .build()
        val config = response.apiConfig()
        assertEquals("https://b.example/", config.origin.toString())
        assertEquals("https://b.example/wp-admin/admin-ajax.php", config.ajaxUrl.toString())
        assertEquals("new-nonce", config.nonce)
        val currentHeaders = Headers.Builder().add("Referer", "https://c.example/").add("Origin", "https://c.example").build()
        val ajaxHeaders = config.headers(currentHeaders, "https://a.example/chapter-1/?view=all")
        assertEquals("https://b.example/chapter-1/?view=all", ajaxHeaders["Referer"])
        assertEquals("https://b.example", ajaxHeaders["Origin"])
    }

    @Test
    fun relativeAjaxEndpointResolvesAgainstTheResponseOrigin() {
        val config = parseApiConfig("var zing = {\"ajax_url\":\"/wp-admin/admin-ajax.php\",\"nonce\":\"nonce\"}", "https://b.example/".toHttpUrl())
        assertEquals("https://b.example/wp-admin/admin-ajax.php", config.ajaxUrl.toString())
    }

    @Test
    fun staleCrossOriginAjaxConfigurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseApiConfig("var zing = {\"ajax_url\":\"https://a.example/wp-admin/admin-ajax.php\",\"nonce\":\"nonce\"}", "https://b.example/".toHttpUrl())
        }
    }

    @Test
    fun anEmptyNonceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseApiConfig("var zing = {\"ajax_url\":\"https://b.example/wp-admin/admin-ajax.php\",\"nonce\":\"\"}", "https://b.example/".toHttpUrl())
        }
    }
}
