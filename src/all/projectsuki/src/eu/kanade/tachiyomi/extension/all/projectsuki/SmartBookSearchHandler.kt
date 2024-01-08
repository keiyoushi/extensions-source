package eu.kanade.tachiyomi.extension.all.projectsuki

import android.icu.text.BreakIterator
import android.icu.text.Collator
import android.icu.text.Normalizer2
import android.icu.text.RuleBasedCollator
import android.icu.text.StringSearch
import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl
import java.text.StringCharacterIterator
import java.util.TreeMap
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

private typealias IsWord = Boolean

/**
 * Handles the "Smart" book search mode, requires [API][RequiresApi] [Build.VERSION_CODES.N]
 * because it uses [android.icu.text] classes.
 *
 * Tries to avoid wasting resource by normalizing text only once and using [ThreadLocal] instances.
 */
@Suppress("MemberVisibilityCanBePrivate")
@RequiresApi(Build.VERSION_CODES.N)
class SmartBookSearchHandler(val rawQuery: String, val rawBooksData: Map<BookID, BookTitle>) {

    data class WordsData(val words: List<String>, val extra: List<String>, val wordRanges: List<IntRange>)

    data class CollatedElement<Cat>(val value: String, val range: IntRange, val origin: String, val category: Cat)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun BreakIterator.collate(text: String): List<CollatedElement<Unit>> = collate(text) {}
    private inline fun <Cat> BreakIterator.collate(text: String, categorizer: (ruleStatus: Int) -> Cat): List<CollatedElement<Cat>> {
        this.text = StringCharacterIterator(text)
        var left = first()
        var right = next()

        return buildList {
            while (right != BreakIterator.DONE) {
                val cat = categorizer(ruleStatus)
                val value = text.substring(left, right)

                add(CollatedElement(value, left..right, text, cat))

                left = right
                right = next()
            }
        }
    }

    private val normQuery by unexpectedErrorCatchingLazy { normalizer.normalize(rawQuery) }

    val wordsData: WordsData by unexpectedErrorCatchingLazy {
        val charBreak = charBreak
        val wordBreak = wordBreak

        val words: List<CollatedElement<IsWord>> = wordBreak.collate(normQuery) { ruleStatus ->
            when (ruleStatus) {
                BreakIterator.WORD_NONE -> false
                else -> true
            }
        }
        val extra: MutableList<String> = ArrayList()

        words.forEach { collatedElement ->
            if (!collatedElement.category) {
                // not a "word" per-say
                extra.addAll(charBreak.collate(collatedElement.value) { Unit }.map { it.value })
            }
        }

        WordsData(
            words = words.filter { it.category }.map { it.value },
            extra = extra,
            wordRanges = words.filter { it.category }.map { it.range },
        )
    }

    val normalizedBooksData: Map<BookID, BookTitle> by unexpectedErrorCatchingLazy {
        val normalizer = normalizer
        rawBooksData.mapValues { normalizer.normalize(it.value) }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun wordScoreFor(matchedText: String): UInt {
        // not starting from index 0 is intentional here
        return fib32.getOrElse(charBreak.collate(matchedText).size) { fib32.last() }
    }

    val filteredBooks: Set<BookID> by unexpectedErrorCatchingLazy {
        val stringSearch = stringSearch
        val wordsData = wordsData
        val booksData: Map<BookID, BookTitle> = normalizedBooksData

        class Counter(var value: UInt)

        val scored: Map<BookID, Counter> = booksData.mapValues { Counter(0u) }

        booksData.forEach { (bookID, normTitle) ->
            val score = scored[bookID]!!
            stringSearch.target = StringCharacterIterator(normTitle)

            wordsData.words.forEach { word ->
                stringSearch.pattern = word
                var idx = stringSearch.first()
                while (idx != StringSearch.DONE) {
                    score.value += wordScoreFor(stringSearch.matchedText)
                    idx = stringSearch.next()
                }
            }

            wordsData.extra.forEach { extra ->
                stringSearch.pattern = extra
                var idx = stringSearch.first()
                while (idx != StringSearch.DONE) {
                    score.value += SCORE_EXTRA
                    idx = stringSearch.next()
                }
            }
        }

        val byScore: TreeMap<UInt, MutableList<Map.Entry<BookID, Counter>>> = scored.entries.groupByTo(TreeMap(reverseOrder<UInt>())) { it.value.value }
        val highest = byScore.firstKey().toFloat()

        val included: MutableSet<BookID> = LinkedHashSet()
        for ((score, group) in byScore) {
            val include = score > 0u && (included.size < MINIMUM_RESULTS || (score.toFloat() / highest) >= NEEDED_FRACTIONAL_SCORE_FOR_INCLUSION)
            if (!include) break

            included.addAll(group.map { it.key })
        }

        included
    }

    val mangasPage: MangasPage by unexpectedErrorCatchingLazy {
        filteredBooks.associateWith { rawBooksData[it]!! }
            .toMangasPage()
    }

    companion object {
        private inline fun <T> threadLocal(crossinline initializer: () -> T) = PropertyDelegateProvider<Any?, ThreadLocal<T>> { _: Any?, _: KProperty<*> ->
            object : ThreadLocal<T>() {
                override fun initialValue(): T? = initializer()
            }
        }

        private operator fun <T> ThreadLocal<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
            return get() ?: reportErrorToUser("SmartBookSearchHandler.${property.name}") { "null initialValue" }
        }

        private val charBreak: BreakIterator by threadLocal { BreakIterator.getCharacterInstance() }
        private val wordBreak: BreakIterator by threadLocal { BreakIterator.getWordInstance() }
        private val normalizer: Normalizer2 by threadLocal { Normalizer2.getNFKCCasefoldInstance() }
        private val collator: RuleBasedCollator by threadLocal {
            (Collator.getInstance() as RuleBasedCollator).apply {
                isCaseLevel = true
                strength = Collator.PRIMARY
                decomposition = Collator.NO_DECOMPOSITION // !! must handle this ourselves !!
            }
        }

        private val stringSearch: StringSearch by threadLocal {
            StringSearch(
                /* pattern = */ "dummy",
                /* target = */ StringCharacterIterator("dummy"),
                /* collator = */ collator,
            ).apply {
                isOverlapping = true
            }
        }

        /** first 32 fibonacci numbers */
        @JvmStatic
        @OptIn(ExperimentalUnsignedTypes::class)
        private val fib32: UIntArray = uintArrayOf(
            1u, 1u, 2u, 3u,
            5u, 8u, 13u, 21u,
            34u, 55u, 89u, 144u,
            233u, 377u, 610u, 987u,
            1597u, 2584u, 4181u, 6765u,
            10946u, 17711u, 28657u, 46368u,
            75025u, 121393u, 196418u, 317811u,
            514229u, 832040u, 1346269u, 2178309u,
        )

        private const val SCORE_EXTRA: UInt = 1u

        private const val NEEDED_FRACTIONAL_SCORE_FOR_INCLUSION: Float = 0.5f
        private const val MINIMUM_RESULTS: Int = 8
    }
}

/** simply creates an https://projectsuki.com/book/<bookid> [HttpUrl] */
internal fun BookID.bookIDToURL(): HttpUrl {
    return homepageUrl.newBuilder()
        .addPathSegment("book")
        .addPathSegment(this)
        .build()
}

internal fun Map<BookID, BookTitle>.toMangasPage(hasNextPage: Boolean = false): MangasPage = entries.toMangasPage(hasNextPage)
internal fun Iterable<Map.Entry<BookID, BookTitle>>.toMangasPage(hasNextPage: Boolean = false): MangasPage {
    return MangasPage(
        mangas = map { (bookID: BookID, bookTitle: BookTitle) ->
            SManga.create().apply {
                title = bookTitle
                url = bookID.bookIDToURL().rawRelative ?: reportErrorToUser { "Could not create relative url for bookID: $bookID" }
                thumbnail_url = bookThumbnailUrl(bookID, "").toUri().toASCIIString()
            }
        },
        hasNextPage = hasNextPage,
    )
}
