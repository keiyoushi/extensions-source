package eu.kanade.tachiyomi.extension.all.projectsuki

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.properties.PropertyDelegateProvider

@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

internal typealias BookID = String
internal typealias ChapterID = String
internal typealias ScanGroup = String

internal fun <R> unexpectedErrorCatchingLazy(mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED, initializer: () -> R): PropertyDelegateProvider<Any?, Lazy<R>> = PropertyDelegateProvider { thisRef, property ->
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

internal fun bookThumbnailUrl(bookID: BookID, extension: String, size: UInt? = null): HttpUrl = homepageUrl.newBuilder()
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

    val psHrefAnchors: Map<Element, HttpUrl> by unexpectedErrorCatchingLazy {
        allHrefAnchors.filterValues { url ->
            url.host.endsWith(homepageUrl.host)
        }
    }

    data class PSBook(val thumbnail: HttpUrl, val rawTitle: String, val bookUrl: HttpUrl, val bookID: BookID) {
        override fun equals(other: Any?) = other is PSBook && this.bookID == other.bookID
        override fun hashCode() = bookID.hashCode()
    }

    val books: Set<PSBook> by unexpectedErrorCatchingLazy {
        buildSet {
            data class BookUrlContainerElement(val container: Element, val href: HttpUrl, val matchResult: PathMatchResult)

            psHrefAnchors.entries
                .map { (element, href) -> BookUrlContainerElement(element, href, href.matchAgainst(bookUrlPattern)) }
                .filter { it.matchResult.doesMatch }
                .groupBy { it.matchResult.group(1)!! }
                .forEach { (bookID: BookID, containers: List<BookUrlContainerElement>) ->

                    val extension: String = containers.asSequence()
                        .flatMap { it.container.select("img") }
                        .firstNotNullOfOrNull { img ->
                            val src = img.imageSrc() ?: return@firstNotNullOfOrNull null
                            val match = src.matchAgainst(thumbnailUrlPattern)
                            if (match.doesMatch) match.group(3, 2) else null
                        } ?: ""

                    val title: String = containers.firstNotNullOfOrNull {
                        val container = it.container
                        if (container.select("img").isEmpty() && container.parents().none { p -> p.tag().normalName() == "small" }) {
                            val text = container.ownText()
                            if (!text.equals("show more", ignoreCase = true)) text else null
                        } else {
                            null
                        }
                    } ?: reportErrorToUser("DataExtractor.books") { "Could not determine title for $bookID" }

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

    data class PSBookDetails(
        val book: PSBook,
        val details: Map<BookDetail, BookDetail.ProcessedData>,
        val alertData: List<String>,
        val description: String,
    ) {
        override fun equals(other: Any?) = other is PSBookDetails && this.book == other.book
        override fun hashCode() = book.hashCode()
    }

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
            internal val chinaRegex: Regex = """cn|china?""".toRegex(RegexOption.IGNORE_CASE)
            internal val japanRegex: Regex = """jp|japan?""".toRegex(RegexOption.IGNORE_CASE)
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

    private val displayNoneRegex = """display: ?none;?""".toRegex(RegexOption.IGNORE_CASE)

    val bookDetails: PSBookDetails by unexpectedErrorCatchingLazy {
        val match = url.matchAgainst(bookUrlPattern)
        if (!match.doesMatch) reportErrorToUser { "cannot extract book details: $url" }
        val bookID = match.group(1)!!

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
            .firstNotNullOfOrNull { e ->
                val src = e.imageSrc() ?: return@firstNotNullOfOrNull null
                val matchUrl = src.matchAgainst(thumbnailUrlPattern)
                if (matchUrl.doesMatch) matchUrl.group(3, 2) else null
            } ?: ""

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

    sealed class ChaptersTableColumnDataType(val required: Boolean) {

        abstract fun isRepresentedBy(from: String): Boolean

        object Chapter : ChaptersTableColumnDataType(required = true) {
            private val chapterHeaderRegex = """chapters?""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(chapterHeaderRegex)
        }

        object Group : ChaptersTableColumnDataType(required = true) {
            private val groupHeaderRegex = """groups?""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(groupHeaderRegex)
        }

        object Added : ChaptersTableColumnDataType(required = true) {
            private val dateHeaderRegex = """added|date""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(dateHeaderRegex)
        }

        object Language : ChaptersTableColumnDataType(required = false) {
            private val languageHeaderRegex = """language""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(languageHeaderRegex)
        }

        object Views : ChaptersTableColumnDataType(required = false) {
            @Suppress("RegExpUnnecessaryNonCapturingGroup")
            private val languageHeaderRegex = """views?(?:\s*count)?""".toRegex(RegexOption.IGNORE_CASE)
            override fun isRepresentedBy(from: String): Boolean = from.matches(languageHeaderRegex)
        }

        companion object {
            val all: Set<ChaptersTableColumnDataType> by unexpectedErrorCatchingLazy { setOf(Chapter, Group, Added, Language, Views) }
            val required: Set<ChaptersTableColumnDataType> by unexpectedErrorCatchingLazy { all.filterTo(LinkedHashSet()) { it.required } }

            fun extractDataTypes(headers: List<Element>): Map<ChaptersTableColumnDataType, Int> = buildMap {
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

    data class BookChapter(
        val chapterUrl: HttpUrl,
        val chapterMatchResult: PathMatchResult,
        val chapterTitle: String,
        val chapterNumber: ChapterNumber?,
        val chapterGroup: ScanGroup,
        val chapterDateAdded: Long,
        val chapterLanguage: String,
    ) {
        @Suppress("unused")
        val bookID: BookID = chapterMatchResult.group(1)!!

        @Suppress("unused")
        val chapterID: ChapterID = chapterMatchResult.group(2)!!
    }

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

                val columnDataTypes: Map<ChaptersTableColumnDataType, Int> =
                    theadElement.select("tr").firstNotNullOfOrNull { headerRow ->
                        ChaptersTableColumnDataType.extractDataTypes(headers = headerRow.select("td"))
                            .takeIf { it.keys.containsAll(ChaptersTableColumnDataType.required) }
                    } ?: return@mapNotNull null

                val dataRows: List<Elements> = tbodyElement.select("tr")
                    .asSequence()
                    .mapNotNull { it.children().takeIf { c -> c.size == columnDataTypes.size } }
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

                        val chapterUrl: HttpUrl = (chapterElement.selectFirst("a[href]") ?: reportErrorToUser { "Could not determine chapter url for ${chapterElement.text()}" })
                            .attr("abs:href")
                            .toHttpUrl()
                        val chapterUrlMatch: PathMatchResult = chapterUrl.matchAgainst(chapterUrlPattern)

                        val chapterNumber: ChapterNumber? = chapterElement.text().tryAnalyzeChapterNumber()
                        val dateAdded: Long = addedElement.text().tryAnalyzeChapterDate()
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

    data class ChapterNumber(val main: UInt, val sub: UInt) : Comparable<ChapterNumber> {
        override fun compareTo(other: ChapterNumber): Int = comparator.compare(this, other)

        companion object {
            val comparator: Comparator<ChapterNumber> by unexpectedErrorCatchingLazy { compareBy({ it.main }, { it.sub }) }
            val chapterNumberRegex: Regex = """(?:chapter|ch\.?)\s*(\d+)(?:\s*[.,-]\s*(\d+)?)?""".toRegex(RegexOption.IGNORE_CASE)
        }
    }

    private fun String.tryAnalyzeChapterNumber(): ChapterNumber? = ChapterNumber.chapterNumberRegex
        .find(this)
        ?.let { simpleMatchResult ->
            val main: UInt = simpleMatchResult.groupValues[1].toUInt()
            val sub: UInt = simpleMatchResult.groupValues[2].takeIf { it.isNotBlank() }?.toUInt() ?: 0u

            ChapterNumber(main, sub)
        }

    data class MissingChapterNumberEdge(val index: Int, val aboveIsKnown: Boolean, val belowIsKnown: Boolean) {
        init {
            require(aboveIsKnown || belowIsKnown) { "previous or next index must be known (or both)" }
        }
    }

    private fun List<BookChapter>.tryInferMissingChapterNumbers(): List<BookChapter> {
        if (isEmpty()) return emptyList()

        val switchingPoints: List<SwitchingPoint> = switchingPoints { it.chapterNumber != null }
        val missingChapterNumberEdges: ArrayDeque<MissingChapterNumberEdge> = ArrayDeque()

        when {
            switchingPoints.isEmpty() && first().chapterNumber == null -> {
                reportErrorToUser { "No chapter numbers could be inferred!" }
            }

            switchingPoints.isEmpty() -> {
                return this
            }
        }

        switchingPoints.forEach { (left, right, leftIsKnown, rightIsKnown) ->
            when {
                leftIsKnown && !rightIsKnown -> {
                    missingChapterNumberEdges.add(MissingChapterNumberEdge(right, aboveIsKnown = true, belowIsKnown = false))
                }

                else -> {
                    val last: MissingChapterNumberEdge? = missingChapterNumberEdges.lastOrNull()
                    when (last?.index == left) {
                        true -> {
                            missingChapterNumberEdges[missingChapterNumberEdges.lastIndex] = MissingChapterNumberEdge(left, aboveIsKnown = true, belowIsKnown = true)
                        }

                        else -> {
                            missingChapterNumberEdges.add(MissingChapterNumberEdge(left, aboveIsKnown = false, belowIsKnown = true))
                        }
                    }
                }
            }
        }

        fun ChapterNumber.predictBelow(): ChapterNumber = when (sub) {
            0u -> ChapterNumber(main - 1u, 0u)
            5u -> ChapterNumber(main, 0u)
            else -> ChapterNumber(main, sub - 1u)
        }

        fun ChapterNumber.predictAbove(): ChapterNumber = when (sub) {
            0u, 5u -> ChapterNumber(main + 1u, 0u)
            else -> ChapterNumber(main, sub + 1u)
        }

        fun MissingChapterNumberEdge.indexAbove(): Int = index - 1
        fun MissingChapterNumberEdge.indexBelow(): Int = index + 1

        val result: MutableList<BookChapter> = ArrayList(this)
        while (missingChapterNumberEdges.isNotEmpty()) {
            val edge: MissingChapterNumberEdge = missingChapterNumberEdges.removeFirst()

            when {
                edge.aboveIsKnown && edge.belowIsKnown -> {
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
                            result[edge.index] = result[edge.index].copy(chapterNumber = inferredByDecreasing)
                        }

                        inferredByIncreasing >= above.chapterNumber || inferredByDecreasing <= below.chapterNumber -> {
                            reportErrorToUser { "Chapter number inference failed (case 2)!" }
                        }

                        inferredByDecreasing > inferredByIncreasing -> {
                            result[edge.index] = result[edge.index].copy(chapterNumber = inferredByIncreasing)
                        }

                        else -> {
                            reportErrorToUser { "Chapter number inference failed (case 3)!" }
                        }
                    }
                }

                edge.aboveIsKnown -> {
                    val above: BookChapter = result[edge.indexAbove()]
                    val inferredByDecreasing = above.chapterNumber!!.predictBelow()

                    result[edge.index] = result[edge.index].copy(chapterNumber = inferredByDecreasing)

                    when (missingChapterNumberEdges.firstOrNull()?.index == edge.index + 1) {
                        true -> {
                            val removed = missingChapterNumberEdges.removeFirst()
                            missingChapterNumberEdges.addFirst(removed.copy(aboveIsKnown = true, belowIsKnown = false))
                        }

                        false -> {
                            missingChapterNumberEdges.addLast(MissingChapterNumberEdge(edge.indexBelow(), aboveIsKnown = true, belowIsKnown = false))
                        }
                    }
                }

                edge.belowIsKnown -> {
                    val below: BookChapter = result[edge.index + 1]
                    val inferredByIncreasing = below.chapterNumber!!.predictAbove()

                    result[edge.index] = result[edge.index].copy(chapterNumber = inferredByIncreasing)

                    when (missingChapterNumberEdges.lastOrNull()?.index == edge.index - 1) {
                        true -> {
                            val removed = missingChapterNumberEdges.removeLast()
                            missingChapterNumberEdges.addLast(removed.copy(aboveIsKnown = true, belowIsKnown = true))
                        }

                        false -> {
                            missingChapterNumberEdges.addLast(MissingChapterNumberEdge(edge.indexAbove(), aboveIsKnown = false, belowIsKnown = true))
                        }
                    }
                }

                else -> {
                    reportErrorToUser { "Chapter number inference failed (case 4)!" }
                }
            }
        }

        return result
    }

    private val absoluteDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val relativeChapterDateRegex = """(\d+)\s+(years?|months?|weeks?|days?|hours?|mins?|minutes?|seconds?|sec)\s+ago""".toRegex(RegexOption.IGNORE_CASE)

    private fun String.tryAnalyzeChapterDate(): Long {
        val match = relativeChapterDateRegex.matchEntire(this.trim()) ?: return absoluteDateFormat.parse(this)?.time ?: 0L

        val number: Int = match.groupValues[1].toInt()
        val relativity: String = match.groupValues[2]
        val cal: Calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US)

        with(relativity) {
            when {
                startsWith("year", true) -> cal.add(Calendar.YEAR, -number)
                startsWith("month", true) -> cal.add(Calendar.MONTH, -number)
                startsWith("week", true) -> cal.add(Calendar.DAY_OF_MONTH, -number * 7)
                startsWith("day", true) -> cal.add(Calendar.DAY_OF_MONTH, -number)
                startsWith("hour", true) -> cal.add(Calendar.HOUR, -number)
                startsWith("min", true) -> cal.add(Calendar.MINUTE, -number)
                startsWith("sec", true) -> cal.add(Calendar.SECOND, -number)
            }
        }

        return cal.timeInMillis
    }
}
