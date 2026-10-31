package eu.kanade.tachiyomi.extension.it.hentaiita

import java.text.Normalizer

/**
 * The site does not link the chapters of a series: they are separate posts
 * whose titles share a base and end with a chapter marker ("Titolo 2",
 * "Titolo [Parte 1]", "Titolo – Prima Parte", "Titolo – Capitoli 1 e 2").
 * A number inside the title ("in meno di 2 minuti") is not a marker.
 */
object SeriesTitle {

    class Split(val base: String, val number: Int?)

    fun split(title: String): Split {
        val text = title.trim().replace(PARENTHESIS, "")
        ORDINAL_PART.find(text)?.takeIf { it.range.first > 0 }?.let {
            return Split(text.substring(0, it.range.first).trim(), ORDINALS[it.groupValues[1].lowercase()])
        }
        for (marker in listOf(LABELLED, TRAILING_NUMBER)) {
            marker.find(text)?.takeIf { it.range.first > 0 }?.let {
                return Split(text.substring(0, it.range.first).trim(), it.groupValues[1].toIntOrNull())
            }
        }
        return Split(text, null)
    }

    // Compare bases without case, accents and punctuation.
    fun key(base: String): String = Normalizer.normalize(base.lowercase(), Normalizer.Form.NFKD)
        .replace(MARKS, "")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

    private val ORDINALS = listOf("prima", "seconda", "terza", "quarta", "quinta", "sesta", "settima", "ottava", "nona", "decima")
        .withIndex()
        .associate { (index, word) -> word to index + 1 }

    private val PARENTHESIS = Regex("""\s*\([^)]*\)\s*$""")
    private val ORDINAL_PART = Regex(
        """[\s\-–—:,.]*\[?\s*(prima|seconda|terza|quarta|quinta|sesta|settima|ottava|nona|decima)\s+parte\s*\]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val LABELLED = Regex(
        """[\s\-–—:,.]*\[?\s*(?:parte|part|capitoli|capitolo|cap\.?|ch\.?|chapter|episodio|ep\.?|vol\.?|volume)\s*(\d+)(?:\s*(?:e|-|–|,)\s*\d+)*\s*\]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_NUMBER = Regex("""\s+(\d+)(?:\s*(?:e|-|–)\s*\d+)*\s*$""")
    private val MARKS = Regex("""\p{M}+""")
    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]+""")
}
