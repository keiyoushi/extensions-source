package keiyoushi.utils

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements

/** Parses the response body as HTML into a [Document], closing the response afterward. */
fun Response.asJsoup(): Document = use { response ->
    Jsoup.parse(
        response.body.byteStream(),
        null,
        response.request.url.toString(),
    )
}

/** Parses the response body with an explicit [parser] (e.g [Parser.xmlParser]) into a [Document], closing the response afterward. */
fun Response.asJsoup(parser: Parser): Document = use { response ->
    Jsoup.parse(
        response.body.byteStream(),
        null,
        response.request.url.toString(),
        parser,
    )
}

/** Parses this HTML string into a [Document], resolving relative links against [baseUrl]. */
fun String.asJsoup(baseUrl: String): Document = Jsoup.parse(this, baseUrl)

/** Parses this HTML string with an explicit [parser] (e.g [Parser.xmlParser]) into a [Document], resolving relative links against [baseUrl]. */
fun String.asJsoup(baseUrl: String, parser: Parser): Document = Jsoup.parse(this, baseUrl, parser)

/** Returns the value of [attributeKey] trimmed, or null when the attribute is missing or blank. */
fun Element.attrOrNull(attributeKey: String): String? = attr(attributeKey).takeUnless { it.isBlank() }?.trim()

/** Returns the last element matching [cssQuery], or null when there is no match. */
fun Element.selectLast(cssQuery: String): Element? = select(cssQuery).lastOrNull()

/** Returns the text of this element, or null when it is empty. */
fun Element.textOrNull(): String? = text().takeUnless { it.isEmpty() }

/** Returns the combined text of all elements in this collection, or null when it is empty. */
fun Elements.textOrNull(): String? = text().takeUnless { it.isEmpty() }

/** Returns the text owned by this element only, excluding child elements, or null when it is empty. */
fun Element.ownTextOrNull(): String? = ownText().takeUnless { it.isEmpty() }
