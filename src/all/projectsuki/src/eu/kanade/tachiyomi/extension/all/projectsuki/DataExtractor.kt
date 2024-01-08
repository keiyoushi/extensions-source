package eu.kanade.tachiyomi.extension.all.projectsuki

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.properties.PropertyDelegateProvider

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

internal typealias BookID = String
internal typealias ChapterID = String
internal typealias ScanGroup = String

/**
 * Creates a [delegate provider](https://kotlinlang.org/docs/delegated-properties.html#providing-a-delegate)
 * that will return a [Lazy] where the [initializer] is wrapped by a try/catch block that will catch all exceptions
 * that aren't a [ProjectSukiException] and constructing a [reportErrorToUser] with a locationHint.
 */
internal fun <R> unexpectedErrorCatchingLazy(mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED, initializer: () -> R): PropertyDelegateProvider<Any?, Lazy<R>> {
    return PropertyDelegateProvider { thisRef, property ->
        lazy(mode) {
            try {
                initializer()
            } catch (exception: Exception) {
                if (exception !is ProjectSukiException) {
                    val locationHint = buildString {
                        when (thisRef) {
                            null -> append("<root>")
                            else -> append(thisRef::class.simpleName)
                        }
                        append('.')
                        append(property.name)
                    }
                    reportErrorToUser(locationHint) { """Unexpected ${exception::class.simpleName}: ${exception.message ?: "<no message>"}""" }
                }
                throw exception
            }
        }
    }
}

/**
 * Gets the thumbnail image for a particular [bookID], [extension] if needed and [size].
 *
 * Not all URLs produced by this function might point to a valid asset.
 */
internal fun bookThumbnailUrl(bookID: BookID, extension: String, size: UInt? = null): HttpUrl {
    return homepageUrl.newBuilder()
        .addPathSegment("images")
        .addPathSegment("gallery")
        .addPathSegment(bookID)
        .addPathSegment(
            when {
                size == null && extension.isBlank() -> "thumb"
                size == null -> "thumb.$extension"
                extension.isBlank() -> "$size-thumb"
                else -> "$size-thumb.$extension"
            },
        )
        .build()
}

/**
 * Finds the nearest common parent between 2 or more [elements] (will return null if [elements].size < 2).
 *
 * If all [elements] are the same element, it will return the element itself.
 *
 * Returns null if the [elements] are not in the same hierarchy (no common parent, e.g. not in the same [Document]).
 */
internal fun nearestCommonParent(elements: Collection<Element>): Element? {
    if (elements.size < 2) return null

    val parents: List<Iterator<Element>> = elements.map { it.parents().reversed().iterator() }
    var lastCommon: Element? = null

    while (true) {
        val layer: MutableSet<Element?> = parents.mapTo(HashSet()) {
            if (it.hasNext()) it.next() else null
        }
        if (null in layer) break
        if (layer.size != 1) break
        lastCommon = layer.single()
    }

    return lastCommon
}

/**
 * Simple Utility class that represents a switching point between 2 patterns given by a certain predicate (see [switchingPoints]).
 *
 * For example in the sequence 111001 there are 2 switching points,
 * the first one is 10, at indexes 2 and 3,
 * and the second one is 01 at indexes 4 and 5.
 *
 * Both indexes and states are given for absolute clarity.
 */
internal data class SwitchingPoint(val left: Int, val right: Int, val leftState: Boolean, val rightState: Boolean) {
    init {
        if (left + 1 != right) {
            reportErrorToUser { "invalid SwitchingPoint: ($left, $right)" }
        }
        if (leftState == rightState) {
            reportErrorToUser { "invalid SwitchingPoint: ($leftState, $rightState)" }
        }
    }
}

/**
 * Function that will return all [SwitchingPoint]s in a certain sequence.
 */
internal fun <E> Iterable<E>.switchingPoints(predicate: (E) -> Boolean): List<SwitchingPoint> {
    val iterator = iterator()
    if (!iterator.hasNext()) return emptyList()

    val points: MutableList<SwitchingPoint> = ArrayList()
    var state: Boolean = predicate(iterator.next())
    var index = 1
    for (element in iterator) {
        val p = predicate(element)
        if (state != p) {
            points.add(SwitchingPoint(left = index - 1, right = index, leftState = state, rightState = p))
            state = p
        }
        index++
    }

    return points
}

/**
 * Utility class that can extract and format data from a certain [extractionElement].
 *
 * Note that a [Document] is also an [Element].
 *
 * The given [extractionElement] must have an [ownerDocument][Element.ownerDocument] with a valid absolute
 * [location][Document.location] (according to [toHttpUrl]).
 *
 * [Lazy] properties are used to allow for the extraction process to happen only once
 * (and for thread safety, see [LazyThreadSafetyMode], [lazy]).
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataExtractor(val extractionElement: Element) {

    private val url: HttpUrl = extractionElement.ownerDocument()?.location()?.toHttpUrlOrNull() ?: reportErrorToUser {
        buildString {
            append("DataExtractor class requires an \"extractionElement\" element ")
            append("that possesses an owner document with a valid absolute location(), but ")
            append(extractionElement.ownerDocument()?.location())
            append(" was found!")
        }
    }

    /**
     * All [anchor](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/a) tags
     * that have a valid url in the [href](https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/href)
     * [attribute](https://developer.mozilla.org/en-US/docs/Glossary/Attribute).
     *
     * To understand the [Element.select] methods, see [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_selectors)
     * and how to use them [to select DOM elements](https://developer.mozilla.org/en-US/docs/Web/API/Document_object_model/Locating_DOM_elements_using_selectors).
     *
     * JSoup's [Element.attr] methods supports the special `abs:<attribute>` syntax when working with relative URLs.
     * It is simply a shortcut to [Element.absUrl], which uses [Document.baseUri].
     */
    val allHrefAnchors: Map<Element, HttpUrl> by unexpectedErrorCatchingLazy {
        buildMap {
            extractionElement.select("a[href]").forEach { a ->
                val href = a.attr("abs:href")
                if (href.isNotBlank()) {
                    href.toHttpUrlOrNull()
                        ?.let { this[a] = it }
                }
            }
        }
    }

    /**
     * Filters [allHrefAnchors] for urls that satisfy `url.host.endsWith(homepageUrl.host)`.
     *
     * Meaning this property contains only elements that redirect to a Project Suki URL.
     */
    val psHrefAnchors: Map<Element, HttpUrl> by unexpectedErrorCatchingLazy {
        allHrefAnchors.filterValues { url ->
            url.host.endsWith(homepageUrl.host)
        }
    }

    /** Utility class that represents a "book" element, identifier by the [bookID]. */
    data class PSBook(val thumbnail: HttpUrl, val rawTitle: String, val bookUrl: HttpUrl, val bookID: BookID) {
        override fun equals(other: Any?) = other is PSBook && this.bookID == other.bookID
        override fun hashCode() = bookID.hashCode()
    }

    /**
     * This property contains all the [books][PSBook] contained in the [extractionElement].
     *
     * Extraction is done by first obtaining all [psHrefAnchors], and using some heuristics
     * to find the [PSBook.rawTitle] and [PSBook.thumbnail]'s extension.
     *
     * Other extensions might use CSS Selectors (see [DataExtractor]) to find these values in a fixed structure.
     * But because [Project Suki](https://projectsuki.com) seems to be done by hand using [Bootstrap](https://getbootstrap.com/),
     * it has a much more volatile structure.
     *
     * To make it possible to maintain this extension, data extraction is done by finding all elements in the page that redirect to
     * book entries, and using generalized heuristics that should be robust to some types of changes.
     * This has the disadvantage of making distinguishing between the different elements in a single page a nightmare,
     * but luckly we don't need to do that for the purposes of a Tachiyomi extension.
     */
    val books: Set<PSBook> by unexpectedErrorCatchingLazy {
        buildSet {
            data class BookUrlContainerElement(val container: Element, val href: HttpUrl, val matchResult: PathMatchResult)

            psHrefAnchors.entries
                .map { (element, href) -> BookUrlContainerElement(element, href, href.matchAgainst(bookUrlPattern)) }
                .filter { it.matchResult.doesMatch }
                .groupBy { it.matchResult["bookid"]!!.value }
                .forEach { (bookID: BookID, containers: List<BookUrlContainerElement>) ->

                    val extension: String = containers.asSequence()
                        .flatMap { it.container.select("img") }
                        .mapNotNull { it.imageSrc() }
                        .map { it.matchAgainst(thumbnailUrlPattern) }
                        .filter { it.doesMatch }
                        .firstOrNull()
                        ?.get("thumbextension")
                        ?.value ?: ""

                    val title: String = containers.asSequence()
                        .map { it.container }
                        .filter { it.select("img").isEmpty() }
                        .filter { it.parents().none { p -> p.tag().normalName() == "small" } }
                        .map { it.ownText() }
                        .filter { !it.equals("show more", ignoreCase = true) }
                        .firstOrNull() ?: reportErrorToUser("DataExtractor.books") { "Could not determine title for $bookID" }

                    add(
                        PSBook(
                            thumbnail = bookThumbnailUrl(bookID, extension),
                            rawTitle = title,
                            bookUrl = homepageUrl.newBuilder()
                                .addPathSegment("book")
                                .addPathSegment(bookID)
                                .build(),
                            bookID = bookID,
                        ),
                    )
                }
        }
    }

    /** Utility class that extends [PSBook], by providing a [details], [alertData] and [description]. */
    data class PSBookDetails(
        val book: PSBook,
        val details: Map<BookDetail, BookDetail.ProcessedData>,
        val alertData: List<String>,
        val description: String,
    ) {
        override fun equals(other: Any?) = other is PSBookDetails && this.book == other.book
        override fun hashCode() = book.hashCode()
    }

    /**
     * Represents a plethora of possibly-present data about some book.
     *
     * The process for extracting the details is described in the KDoc for [bookDetails].
     */
    @Suppress("RegExpUnnecessaryNonCapturingGroup")
    sealed class BookDetail {

        open fun tryFind(extractor: DataExtractor): Collection<Element> = emptyList()

        abstract val regex: Regex
        fun process(element: Element): ProcessedData = ProcessedData(label(element), detailsData(element))
        abstract fun label(element: Element?): String
        abstract fun detailsData(element: Element): String

        data class ProcessedData(val label: String, val detailData: String)

        object AltTitle : BookDetail() {
            override val regex: Regex = """(?:alternative|alt\.?) titles?:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Alt titles:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Author : BookDetail() {
            override fun tryFind(extractor: DataExtractor): Collection<Element> = extractor.psHrefAnchors.filter { (_, url) ->
                url.queryParameterNames.contains("author")
            }.keys

            override val regex: Regex = """authors?:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Authors:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Artist : BookDetail() {
            override fun tryFind(extractor: DataExtractor): Collection<Element> = extractor.psHrefAnchors.filter { (_, url) ->
                url.queryParameterNames.contains("artist")
            }.keys

            override val regex: Regex = """artists?:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Artists:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Status : BookDetail() {
            override fun tryFind(extractor: DataExtractor): Collection<Element> = extractor.psHrefAnchors.filter { (_, url) ->
                url.queryParameterNames.contains("status")
            }.keys

            override val regex: Regex = """status:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Status:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Origin : BookDetail() {
            override fun tryFind(extractor: DataExtractor): Collection<Element> = extractor.psHrefAnchors.filter { (_, url) ->
                url.queryParameterNames.contains("origin")
            }.keys

            override val regex: Regex = """origin:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Origin:"
            override fun detailsData(element: Element): String = element.text()

            internal val koreaRegex: Regex = """kr|korea\s*(?:\(south\))?""".toRegex(RegexOption.IGNORE_CASE)
            internal val chinaRegex: Regex = """kr|korea\s*(?:\(south\))?""".toRegex(RegexOption.IGNORE_CASE)
            internal val japanRegex: Regex = """kr|korea\s*(?:\(south\))?""".toRegex(RegexOption.IGNORE_CASE)
        }

        object ReleaseYear : BookDetail() {
            override val regex: Regex = """release(?: year):?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Release year:"
            override fun detailsData(element: Element): String = element.text()
        }

        object UserRating : BookDetail() {
            override fun tryFind(extractor: DataExtractor): Collection<Element> = extractor.extractionElement.select("#ratings")

            override val regex: Regex = """user ratings?:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "User rating:"
            override fun detailsData(element: Element): String {
                val rates = when {
                    element.id() != "ratings" -> 0
                    else -> element.children().count { it.hasClass("text-warning") }
                }

                return when (rates) {
                    in 1..5 -> "$rates/5"
                    else -> "?/5"
                }
            }
        }

        object Views : BookDetail() {
            override val regex: Regex = """views?:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Views:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Official : BookDetail() {
            override val regex: Regex = """official:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Official:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Purchase : BookDetail() {
            override val regex: Regex = """purchase:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Purchase:"
            override fun detailsData(element: Element): String = element.text()
        }

        object Genre : BookDetail() {
            override fun tryFind(extractor: DataExtractor): Collection<Element> = extractor.psHrefAnchors.filter { (_, url) ->
                url.matchAgainst(genreSearchUrlPattern).doesMatch
            }.keys

            override val regex: Regex = """genre(?:\(s\))?:?""".toRegex(RegexOption.IGNORE_CASE)
            override fun label(element: Element?) = "Genres:"
            override fun detailsData(element: Element): String = element.text()
        }

        companion object {
            val all: List<BookDetail> = listOf(AltTitle, Author, Artist, Status, Origin, ReleaseYear, UserRating, Views, Official, Purchase, Genre)
            fun from(type: String): BookDetail? = all.firstOrNull { it.regex.matches(type) }
        }
    }

    /** Used to detect visible/invisible alerts. */
    private val displayNoneRegex = """display: ?none;?""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * All [details][PSBookDetails] are extracted from a table-like list of `<div>` elements,
     * found in the book main page, using generalized heuristics:
     *
     * First the algorithm looks for known entries in the "table" by looking for
     * the [Status][BookDetail.Status] and [Origin][BookDetail.Origin] fields.
     * This is possible because these elements redirect to the [search](https://projectsuki.com/search)
     * page with "status" and "origin" queries.
     *
     * The [nearestCommonParent] between the two elements is found and the table is subsequently analyzed.
     * If this method fails, at least the [Author][BookDetail.Author], [Artist][BookDetail.Artist] and [Genre][BookDetail.Genre]
     * details are found via URLs.
     *
     * An extra [Genre][BookDetail.Genre] is added when possible:
     *  - Origin: "kr" -> Genre: "Manhwa"
     *  - Origin: "cn" -> Genre: "Manhua"
     *  - Origin: "jp" -> Genre: "Manga"
     *
     * The book title, description and alerts are also found in similar ways.
     *
     * The description is expanded with all this information too.
     */
    val bookDetails: PSBookDetails by unexpectedErrorCatchingLazy {
        val match = url.matchAgainst(bookUrlPattern)
        if (!match.doesMatch) reportErrorToUser { "cannot extract book details: $url" }
        val bookID = match["bookid"]!!.value

        fun tryFindDetailsTable(): Element? {
            val found: Map<BookDetail, Collection<Element>> = BookDetail.all
                .associateWith { it.tryFind(extractor = this) }
                .filterValues { it.isNotEmpty() }

            return nearestCommonParent(found.values.flatMapTo(LinkedHashSet()) { it })
        }

        val detailsTable: Element? = tryFindDetailsTable()
        val rows: List<Element> = detailsTable?.children()?.toList() ?: emptyList()
        val details: MutableMap<BookDetail, BookDetail.ProcessedData> = LinkedHashMap()

        for (row in rows) {
            val cols = row.children()
            val typeElement = cols.getOrNull(0) ?: continue
            val valueElement = cols.getOrNull(1) ?: continue

            val typeText = typeElement.text()
            val detail = BookDetail.from(typeText) ?: continue

            details[detail] = detail.process(valueElement)
        }

        run {
            val originGenre: String? = details[BookDetail.Origin]?.detailData?.let { originData ->
                when {
                    originData.matches(BookDetail.Origin.koreaRegex) -> "Manhwa"
                    originData.matches(BookDetail.Origin.chinaRegex) -> "Manhua"
                    originData.matches(BookDetail.Origin.japanRegex) -> "Manga"
                    else -> null
                }
            }

            if (originGenre != null) {
                details[BookDetail.Genre] = when (details.containsKey(BookDetail.Genre)) {
                    true -> {
                        val (label, data) = details[BookDetail.Genre]!!
                        BookDetail.ProcessedData(label, if (data.isBlank()) originGenre else """$data, $originGenre""")
                    }
                    false -> {
                        BookDetail.ProcessedData(BookDetail.Genre.label(null), originGenre)
                    }
                }
            }
        }

        val title: Element? = extractionElement.selectFirst("h2[itemprop=title]") ?: extractionElement.selectFirst("h2") ?: run {
            // the common table is inside of a "row" wrapper that is the neighbour of the h2 containing the title
            // if we sort of generalize this, the title should be the first
            // text-node-bearing child of the table's grandparent
            detailsTable?.parent()?.parent()?.children()?.firstOrNull { it.textNodes().isNotEmpty() }
        }

        val alerts: List<String> = extractionElement.select(".alert, .alert-info")
            .asSequence()
            .filter { !it.attr("style").contains(displayNoneRegex) }
            .filter { alert -> alert.parents().none { it.attr("style").contains(displayNoneRegex) } }
            .map { alert ->
                buildString {
                    var appendedSomething = false
                    alert.select("h4").singleOrNull()?.let {
                        appendLine(it.wholeText())
                        appendedSomething = true
                    }
                    alert.select("p").singleOrNull()?.let {
                        appendLine(it.wholeText())
                        appendedSomething = true
                    }
                    if (!appendedSomething) {
                        appendLine(alert.wholeText())
                    }
                }
            }
            .toList()

        val description = extractionElement.selectFirst("#descriptionCollapse")
            ?.wholeText() ?: extractionElement.select(".description")
            .joinToString("\n\n", postfix = "\n") { it.wholeText() }

        val extension = extractionElement.select("img")
            .asSequence()
            .mapNotNull { e -> e.imageSrc()?.let { e to it } }
            .map { (img, src) -> img to src.matchAgainst(thumbnailUrlPattern) }
            .filter { (_, match) -> match.doesMatch }
            .firstOrNull()
            ?.second
            ?.get("thumbextension")
            ?.value ?: ""

        PSBookDetails(
            book = PSBook(
                bookThumbnailUrl(bookID, extension),
                title?.text() ?: reportErrorToUser("DataExtractor.bookDetails") { "could not determine title for $bookID" },
                url,
                bookID,
            ),
            details = details,
            alertData = alerts,
            description = description,
        )
    }

    /** Represents some data type that a certain column in the chapters table represents. */
    sealed class ChaptersTableColumnDataType(val required: Boolean) {

        /** @return true if this data type is represented by a column's raw title. */
        abstract fun isRepresentedBy(from: String): Boolean

        /** Represents the chapter's title, which also normally includes the chapter number. */
        /*data*/ object Chapter : ChaptersTableColumnDataType(required = true) {
            private val chapterHeaderRegex = """chapters?""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(chapterHeaderRegex)
        }

        /** Represents the chapter's scan group. */
        /*data*/ object Group : ChaptersTableColumnDataType(required = true) {
            private val groupHeaderRegex = """groups?""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(groupHeaderRegex)
        }

        /** Represents the chapter's release date (when it was added to the site). */
        /*data*/ object Added : ChaptersTableColumnDataType(required = true) {
            private val dateHeaderRegex = """added|date""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(dateHeaderRegex)
        }

        /** Represents the chapter's language. */
        /*data*/ object Language : ChaptersTableColumnDataType(required = false) {
            private val languageHeaderRegex = """language""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(languageHeaderRegex)
        }

        /** Represents the chapter's view count. */
        /*data*/ object Views : ChaptersTableColumnDataType(required = false) {
            @Suppress("RegExpUnnecessaryNonCapturingGroup")
            private val languageHeaderRegex = """views?(?:\s*count)?""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(languageHeaderRegex)
        }

        companion object {
            val all: Set<ChaptersTableColumnDataType> by unexpectedErrorCatchingLazy { setOf(Chapter, Group, Added, Language, Views) }
            val required: Set<ChaptersTableColumnDataType> by unexpectedErrorCatchingLazy { all.filterTo(LinkedHashSet()) { it.required } }

            /**
             * Takes the list of [headers] and returns a map that
             * represents which data type is contained in which column index.
             *
             * Not all column indexes might be present if some column isn't recognised as a data type listed above.
             */
            fun extractDataTypes(headers: List<Element>): Map<ChaptersTableColumnDataType, Int> {
                return buildMap {
                    headers.map { it.text() }
                        .forEachIndexed { columnIndex, columnHeaderText ->
                            all.forEach { dataType ->
                                if (dataType.isRepresentedBy(columnHeaderText)) {
                                    put(dataType, columnIndex)
                                }
                            }
                        }
                }
            }
        }
    }

    /** Represents a book's chapter. */
    data class BookChapter(
        val chapterUrl: HttpUrl,
        val chapterMatchResult: PathMatchResult,
        val chapterTitle: String,
        val chapterNumber: ChapterNumber?,
        val chapterGroup: ScanGroup,
        val chapterDateAdded: Date?,
        val chapterLanguage: String,
    ) {

        @Suppress("unused")
        val bookID: BookID = chapterMatchResult["bookid"]!!.value

        @Suppress("unused")
        val chapterID: ChapterID = chapterMatchResult["chapterid"]!!.value
    }

    /**
     * This property contains all the [BookChapter]s contained in the [extractionElement], grouped by the [ScanGroup].
     *
     * The extraction proceeds by first finding all `<table>` elements and then progressively refines
     * the extracted data to remove false positives, combining all the extracted data and removing duplicates at the end.
     *
     * The `<thead>` element is analyzed to find the corresponding data types, this is resistant to shuffles
     * (e.g. if the Chapter and Language columns are swapped, this will work anyways).
     *
     * Then the `<tbody>` rows (`<tr>`) are one by one processed to find the ones that match the column (`<td>`)
     * size and data type positions that we care about.
     */
    val bookChapters: Map<ScanGroup, List<BookChapter>> by unexpectedErrorCatchingLazy {
        data class RawTable(val self: Element, val thead: Element, val tbody: Element)
        data class AnalyzedTable(val raw: RawTable, val columnDataTypes: Map<ChaptersTableColumnDataType, Int>, val dataRows: List<Elements>)

        val allChaptersByGroup: MutableMap<ScanGroup, MutableList<BookChapter>> = extractionElement.select("table")
            .asSequence()
            .mapNotNull { tableElement ->
                tableElement.selectFirst("thead")?.let { thead ->
                    tableElement.selectFirst("tbody")?.let { tbody ->
                        RawTable(tableElement, thead, tbody)
                    }
                }
            }
            .mapNotNull { rawTable ->
                val (_: Element, theadElement: Element, tbodyElement: Element) = rawTable

                val columnDataTypes: Map<ChaptersTableColumnDataType, Int> = theadElement.select("tr").asSequence()
                    .mapNotNull { headerRow ->
                        ChaptersTableColumnDataType.extractDataTypes(headers = headerRow.select("td"))
                            .takeIf { it.keys.containsAll(ChaptersTableColumnDataType.required) }
                    }
                    .firstOrNull() ?: return@mapNotNull null

                val dataRows: List<Elements> = tbodyElement.select("tr")
                    .asSequence()
                    .map { it.children() }
                    .filter { it.size == columnDataTypes.size }
                    .toList()

                AnalyzedTable(rawTable, columnDataTypes, dataRows)
            }
            .map { analyzedTable ->
                val (_: RawTable, columnDataTypes: Map<ChaptersTableColumnDataType, Int>, dataRows: List<Elements>) = analyzedTable

                val rawData: List<Map<ChaptersTableColumnDataType, Element>> = dataRows.map { row ->
                    columnDataTypes.mapValues { (_, columnIndex) ->
                        row[columnIndex]
                    }
                }

                val rawByGroup: Map<ScanGroup, List<Map<ChaptersTableColumnDataType, Element>>> = rawData.groupBy { data ->
                    data[ChaptersTableColumnDataType.Group]!!.text()
                }

                val chaptersByGroup: Map<ScanGroup, List<BookChapter>> = rawByGroup.mapValues { (groupName, chapters: List<Map<ChaptersTableColumnDataType, Element>>) ->
                    chapters.map { data: Map<ChaptersTableColumnDataType, Element> ->
                        val chapterElement: Element = data[ChaptersTableColumnDataType.Chapter]!!
                        val addedElement: Element = data[ChaptersTableColumnDataType.Added]!!
                        val languageElement: Element? = data[ChaptersTableColumnDataType.Language]
                        // val viewsElement = data[ChaptersTableColumnDataType.Views]

                        val chapterUrl: HttpUrl = (chapterElement.selectFirst("a[href]") ?: reportErrorToUser { "Could not determine chapter url for ${chapterElement.text()}" })
                            .attr("abs:href")
                            .toHttpUrl()
                        val chapterUrlMatch: PathMatchResult = chapterUrl.matchAgainst(chapterUrlPattern)

                        val chapterNumber: ChapterNumber? = chapterElement.text().tryAnalyzeChapterNumber()
                        val dateAdded: Date? = addedElement.text().tryAnalyzeChapterDate()
                        val chapterLanguage: String = languageElement?.text()?.trim()?.lowercase(Locale.US) ?: UNKNOWN_LANGUAGE

                        BookChapter(
                            chapterUrl = chapterUrl,
                            chapterMatchResult = chapterUrlMatch,
                            chapterTitle = chapterElement.text(),
                            chapterNumber = chapterNumber,
                            chapterGroup = groupName,
                            chapterDateAdded = dateAdded,
                            chapterLanguage = chapterLanguage,
                        )
                    }
                }

                chaptersByGroup
            }
            .map { chaptersByGroup ->
                chaptersByGroup.mapValues { (_, chapters) ->
                    chapters.tryInferMissingChapterNumbers()
                }
            }
            .fold(LinkedHashMap()) { map, next ->
                map.apply {
                    next.forEach { (group, chapters) ->
                        getOrPut(group) { ArrayList() }.addAll(chapters)
                    }
                }
            }

        allChaptersByGroup
    }

    /**
     * Utility class that represents a chapter number.
     *
     * Ordering is implemented in the way a human would most likely expect chapters to be ordered,
     * e.g. chapter 10.15 comes after chapter 10.9
     */
    data class ChapterNumber(val main: UInt, val sub: UInt) : Comparable<ChapterNumber> {
        override fun compareTo(other: ChapterNumber): Int = comparator.compare(this, other)

        companion object {
            val comparator: Comparator<ChapterNumber> by unexpectedErrorCatchingLazy { compareBy({ it.main }, { it.sub }) }
            val chapterNumberRegex: Regex = """(?:chapter|ch\.?)\s*(\d+)(?:\s*[.,-]\s*(\d+)?)?""".toRegex(RegexOption.IGNORE_CASE)
        }
    }

    /** Tries to infer the chapter number from the raw title. */
    private fun String.tryAnalyzeChapterNumber(): ChapterNumber? {
        return ChapterNumber.chapterNumberRegex
            .find(this)
            ?.let { simpleMatchResult ->
                val main: UInt = simpleMatchResult.groupValues[1].toUInt()
                val sub: UInt = simpleMatchResult.groupValues[2].takeIf { it.isNotBlank() }?.toUInt() ?: 0u

                ChapterNumber(main, sub)
            }
    }

    /**
     * Represents an index where the chapter number is unknown and
     * whether or not the previous (above, next numerical chapter)
     * or next (below, previous numerical chapter) chapter numbers
     * are known.
     *
     * Requires [aboveIsKnown] or [belowIsKnown] to be true (or both).
     */
    data class MissingChapterNumberEdge(val index: Int, val aboveIsKnown: Boolean, val belowIsKnown: Boolean) {
        init {
            require(aboveIsKnown || belowIsKnown) { "previous or next index must be known (or both)" }
        }
    }

    /**
     * Chapter titles usually contain "Chapter xx" or "Ch. xx", but to provide some way to patch
     * eventual holes (which happened before with "Ch." which wasn't accounted for), this method is provided.
     *
     * The algorithm tries to infer the chapter numbers by using correctly
     * inferred zones and expanding them.
     *
     * The theoretical behaviour of this algorithm can easily be represented by
     * using + for known and - for unknown chapter numbers
     * (think of a 1D cellular automaton with very simple rules).
     * An example (coarse) timeline could look like this:
     * ```
     * -++--++---+-+++--
     * ++++++++-+++++++-
     * +++++++++++++++++
     * ```
     * The actual changes always happen in a loop-like behaviour from left to right.
     * We can use this to our advantage.
     *
     * Inference is done on a best-guess basis based on neighbouring values.
     * Reporting to the user is preferred to avoid providing weird values.
     */
    private fun List<BookChapter>.tryInferMissingChapterNumbers(): List<BookChapter> {
        if (isEmpty()) return emptyList()

        val switchingPoints: List<SwitchingPoint> = switchingPoints { it.chapterNumber != null }
        val missingChapterNumberEdges: ArrayDeque<MissingChapterNumberEdge> = ArrayDeque()

        when {
            switchingPoints.isEmpty() && first().chapterNumber == null -> {
                // oh dear, nothing is known
                reportErrorToUser { "No chapter numbers could be inferred!" }
            }

            switchingPoints.isEmpty() /* && first().chapterNumber != null */ -> {
                // all are known
                return this
            }
        }

        // convert switching points into an easier-to-handle format
        switchingPoints.forEach { (left, right, leftIsKnown, rightIsKnown) ->
            when {
                leftIsKnown && !rightIsKnown -> {
                    // going from known to unknown in top to bottom direction
                    // chapters go in inverse order, so top is last, bottom is first
                    // left is top, right is bottom.
                    // subject of discussion is the right one (the unknown).
                    // this is the simpler case because we're going from known numbers
                    // to unknown.
                    missingChapterNumberEdges.add(MissingChapterNumberEdge(right, aboveIsKnown = true, belowIsKnown = false))
                }

                else -> {
                    // SwitchingPoint contract's guarantees: leftIsKnown = false, rightIsKnown = true

                    // we were on "unknown" territory, and going to known
                    // subject of discussion is the left one (the unknown).
                    // there is a special case in which the unknown chapter is only one
                    // with known numbers in both directions.
                    // we need to account for that by checking if the last added member
                    // of missingChapterNumberEdges (if any) has index equal to "left" element
                    // (the subject, unknown)
                    // in which case we replace it, with a bi-directional MissingChapterNumberEdge
                    val last: MissingChapterNumberEdge? = missingChapterNumberEdges.lastOrNull()
                    when (last?.index == left) {
                        true -> {
                            // surrounded, replace
                            missingChapterNumberEdges[missingChapterNumberEdges.lastIndex] = MissingChapterNumberEdge(left, aboveIsKnown = true, belowIsKnown = true)
                        }

                        else -> {
                            // 2 or more unknown sequence
                            missingChapterNumberEdges.add(MissingChapterNumberEdge(left, aboveIsKnown = false, belowIsKnown = true))
                        }
                    }
                }
            }
        }

        // previous chapter number
        fun ChapterNumber.predictBelow(): ChapterNumber = when (sub) {
            0u -> ChapterNumber(main - 1u, 0u) // before chapter 18, chapter 17
            5u -> ChapterNumber(main, 0u) // before chapter 18.5, chapter 18
            else -> ChapterNumber(main, sub - 1u) // before chapter 18.4, chapter 18.3
        }

        // next chapter number
        fun ChapterNumber.predictAbove(): ChapterNumber = when (sub) {
            0u, 5u -> ChapterNumber(main + 1u, 0u) // after chapter 17 or 17.5, chapter 18
            else -> ChapterNumber(main, sub + 1u) // after chapter 18.3, 18.4
        }

        fun MissingChapterNumberEdge.indexAbove(): Int = index - 1
        fun MissingChapterNumberEdge.indexBelow(): Int = index + 1

        val result: MutableList<BookChapter> = ArrayList(this)
        while (missingChapterNumberEdges.isNotEmpty()) {
            val edge: MissingChapterNumberEdge = missingChapterNumberEdges.removeFirst()

            when {
                edge.aboveIsKnown && edge.belowIsKnown -> {
                    // both are known
                    val above: BookChapter = result[edge.indexAbove()]
                    val below: BookChapter = result[edge.indexBelow()]

                    val inferredByDecreasing = above.chapterNumber!!.predictBelow()
                    val inferredByIncreasing = below.chapterNumber!!.predictAbove()

                    when {
                        above.chapterNumber == below.chapterNumber -> {
                            reportErrorToUser { "Chapter number inference failed (case 0)!" }
                        }

                        above.chapterNumber < below.chapterNumber -> {
                            reportErrorToUser { "Chapter number inference failed (case 1)!" }
                        }

                        inferredByDecreasing == inferredByIncreasing -> {
                            // inference agrees from both sides
                            result[edge.index] = result[edge.index].copy(chapterNumber = inferredByDecreasing)
                        }

                        // might be handled by above, just for safety
                        inferredByIncreasing >= above.chapterNumber || inferredByDecreasing <= below.chapterNumber -> {
                            reportErrorToUser { "Chapter number inference failed (case 2)!" }
                        }

                        inferredByDecreasing > inferredByIncreasing -> {
                            // gap between chapters, take the lowest
                            result[edge.index] = result[edge.index].copy(chapterNumber = inferredByIncreasing)
                        }

                        else -> {
                            // inferredByIncreasing > inferredByDecreasing should be handled by branch 2 above
                            // everything else should be reported to user
                            reportErrorToUser { "Chapter number inference failed (case 3)!" }
                        }
                    }
                }

                edge.aboveIsKnown -> {
                    // only above is known
                    val above: BookChapter = result[edge.indexAbove()]
                    val inferredByDecreasing = above.chapterNumber!!.predictBelow()

                    // handle this one
                    result[edge.index] = result[edge.index].copy(chapterNumber = inferredByDecreasing)

                    // there are 2 main cases, where + is known, - is unknown, * just changed above and . is anything
                    // case 1: ..+*-+..
                    // case 2: ..+*--..
                    when (missingChapterNumberEdges.firstOrNull()?.index == edge.index + 1) {
                        true -> {
                            // replace next edge with surrounded
                            val removed = missingChapterNumberEdges.removeFirst()
                            missingChapterNumberEdges.addFirst(removed.copy(aboveIsKnown = true, belowIsKnown = false))
                        }

                        false -> {
                            // add new edge below current edge's index
                            missingChapterNumberEdges.addLast(MissingChapterNumberEdge(edge.indexBelow(), aboveIsKnown = true, belowIsKnown = false))
                        }
                    }
                }

                edge.belowIsKnown -> {
                    // only below is known
                    val below: BookChapter = result[edge.index + 1]
                    val inferredByIncreasing = below.chapterNumber!!.predictAbove()

                    // handle this one
                    result[edge.index] = result[edge.index].copy(chapterNumber = inferredByIncreasing)

                    // there are 2 main cases (like see above):
                    // case 1: ..+-*+..
                    // case 2: ..--*+..
                    when (missingChapterNumberEdges.lastOrNull()?.index == edge.index - 1) {
                        true -> {
                            // replace last edge with surrounded
                            val removed = missingChapterNumberEdges.removeLast()
                            missingChapterNumberEdges.addLast(removed.copy(aboveIsKnown = true, belowIsKnown = true))
                        }

                        false -> {
                            // add new edge above current edge's index
                            missingChapterNumberEdges.addLast(MissingChapterNumberEdge(edge.indexAbove(), aboveIsKnown = false, belowIsKnown = true))
                        }
                    }
                }

                else -> {
                    // shouldn't be possible
                    reportErrorToUser { "Chapter number inference failed (case 4)!" }
                }
            }
        }

        return result
    }

    /**
     * ThreadLocal [SimpleDateFormat] (SimpleDateFormat is not thread safe).
     */
    private val absoluteDateFormat: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = runCatching { SimpleDateFormat("MMMM dd, yyyy", Locale.US) }.fold(
            onSuccess = { it },
            onFailure = { reportErrorToUser { "Invalid SimpleDateFormat(MMMM dd, yyyy)" } },
        )
    }

    private val relativeChapterDateRegex = """(\d+)\s+(years?|months?|weeks?|days?|hours?|mins?|minutes?|seconds?|sec)\s+ago""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Tries to parse a possibly human-readable relative [Date].
     *
     * @see Calendar
     */
    private fun String.tryAnalyzeChapterDate(): Date? {
        return when (val match = relativeChapterDateRegex.matchEntire(trim())) {
            null -> {
                absoluteDateFormat.get()
                    .runCatching { this!!.parse(this@tryAnalyzeChapterDate) }
                    .fold(
                        onSuccess = { it },
                        onFailure = { reportErrorToUser { "Could not parse date: $this" } },
                    )
            }

            else -> {
                // relative
                val number: Int = match.groupValues[1].toInt()
                val relativity: String = match.groupValues[2]
                val cal: Calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US)

                with(relativity) {
                    when {
                        startsWith("year") -> cal.add(Calendar.YEAR, -number)
                        startsWith("month") -> cal.add(Calendar.MONTH, -number)
                        startsWith("week") -> cal.add(Calendar.DAY_OF_MONTH, -number * 7)
                        startsWith("day") -> cal.add(Calendar.DAY_OF_MONTH, -number)
                        startsWith("hour") -> cal.add(Calendar.HOUR, -number)
                        startsWith("min") -> cal.add(Calendar.MINUTE, -number)
                        startsWith("sec") -> cal.add(Calendar.SECOND, -number)
                    }
                }

                cal.time
            }
        }
    }
}
