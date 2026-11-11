package keiyoushi.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFile

object Stubs {
    val okhttp: TestFile = kotlin(
        """
        package okhttp3

        class HttpUrl {
            override fun toString(): String = ""
            class Builder { fun build(): HttpUrl = HttpUrl() }
            companion object { fun String.toHttpUrl(): HttpUrl = HttpUrl() }
        }
        class Headers {
            fun newBuilder(): Builder = Builder()
            class Builder {
                fun add(name: String, value: String): Builder = this
                fun set(name: String, value: String): Builder = this
                fun removeAll(name: String): Builder = this
                fun build(): Headers = Headers()
            }
        }
        class Request {
            class Builder {
                fun header(name: String, value: String): Builder = this
                fun addHeader(name: String, value: String): Builder = this
                fun build(): Request = Request()
            }
        }
        abstract class ResponseBody { fun string(): String = "" }
        class Response { val body: ResponseBody get() = TODO() }
        interface Call { fun execute(): Response }
        class OkHttpClient { fun newCall(request: Request): Call = TODO() }
        fun interface Interceptor {
            fun intercept(chain: Chain): Response
            interface Chain { fun proceed(request: Request): Response; fun request(): Request }
        }
        class MediaType {
            companion object { fun String.toMediaType(): MediaType = MediaType() }
        }
        """,
    ).indented()

    val jsoup: TestFile = kotlin(
        """
        package org.jsoup.nodes

        open class Element {
            fun select(query: String): org.jsoup.select.Elements = TODO()
            fun selectFirst(query: String): Element? = null
            fun text(): String = ""
            fun ownText(): String = ""
            fun attr(name: String): String = ""
            fun absUrl(name: String): String = ""
        }
        class Document : Element()
        """,
    ).indented()

    val jsoupElements: TestFile = kotlin(
        """
        package org.jsoup.select

        import org.jsoup.nodes.Element

        class Elements : ArrayList<Element>() {
            fun text(): String = ""
            override fun first(): Element = TODO()
        }
        """,
    ).indented()

    val jsoupParser: TestFile = kotlin(
        """
        package org.jsoup

        object Jsoup { @JvmStatic fun parse(html: String): org.jsoup.nodes.Document = TODO() }
        """,
    ).indented()

    val serialization: TestFile = kotlin(
        """
        package kotlinx.serialization

        annotation class Serializable
        annotation class SerialName(val value: String)
        """,
    ).indented()

    val json: TestFile = kotlin(
        """
        package kotlinx.serialization.json

        sealed class JsonElement
        class JsonObject : JsonElement() { operator fun get(key: String): JsonElement? = null }
        class JsonPrimitive : JsonElement() { val content: String = "" }
        val JsonElement.jsonObject: JsonObject get() = TODO()
        val JsonElement.jsonPrimitive: JsonPrimitive get() = TODO()
        open class Json { fun <T> decodeFromString(string: String): T = TODO(); companion object Default : Json() }
        """,
    ).indented()

    val model: TestFile = kotlin(
        """
        package eu.kanade.tachiyomi.source.model

        interface SManga {
            var url: String
            var title: String
            var thumbnail_url: String?
            companion object { fun create(): SManga = TODO() }
        }
        interface SChapter {
            var url: String
            var name: String
            var chapter_number: Float
            companion object { fun create(): SChapter = TODO() }
        }
        class Page(val index: Int, val url: String = "", var imageUrl: String? = null)
        """,
    ).indented()

    val source: TestFile = kotlin(
        """
        package eu.kanade.tachiyomi.source.online

        abstract class HttpSource {
            abstract val baseUrl: String
            open val supportsLatest: Boolean get() = true
            val headers: okhttp3.Headers get() = TODO()
            protected fun headersBuilder(): okhttp3.Headers.Builder = TODO()
            val client: okhttp3.OkHttpClient get() = TODO()
        }
        """,
    ).indented()

    val keiSource: TestFile = kotlin(
        """
        package keiyoushi.source

        abstract class KeiSource : eu.kanade.tachiyomi.source.online.HttpSource() {
            override val supportsLatest get() = true
        }
        """,
    ).indented()

    val network: TestFile = kotlin(
        """
        package keiyoushi.network

        import okhttp3.HttpUrl
        import okhttp3.OkHttpClient
        import okhttp3.Response

        suspend fun OkHttpClient.get(url: String): Response = TODO()
        suspend fun OkHttpClient.get(url: HttpUrl): Response = TODO()
        """,
    ).indented()

    val android: TestFile = kotlin(
        """
        package android.util

        object Log { @JvmStatic fun d(tag: String, msg: String): Int = 0 }
        """,
    ).indented()

    val all = arrayOf(okhttp, jsoup, jsoupElements, jsoupParser, serialization, json, model, source, keiSource, network, android)
}
