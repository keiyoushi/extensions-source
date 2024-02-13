package eu.kanade.tachiyomi.extension.ja.manga9co

/**
 * https://syosetu.me/ is not added because of different HTML structure
 */

internal const val MIRROR_PREF = "MIRROR"
internal val MIRRORS get() = arrayOf("manga1001.in", "mangaraw.to", "mangaraw.io", "mangarawjp.io")
internal val PROMPT get() = "Note: 'manga1001.in' is not recommended because it might contain shuffled/unordered pages."

internal const val RANDOM_MIRROR_FROM = 1
internal const val RANDOM_MIRROR_UNTIL = 4

internal fun getSelectors(mirrorIndex: Int) = when (mirrorIndex) {
    0, 1, 2 -> Selectors(
        listMangaSelector = ".card",
        detailsSelector = "div:has(> main)",
        recommendClass = "container",
    )
    else -> Selectors(
        listMangaSelector = ".post-list:not(.last-hidden) > .item",
        detailsSelector = "#post-data",
        recommendClass = "post-list",
    )
}

internal fun needUrlSanitize(mirrorIndex: Int) = mirrorIndex == 2

internal fun isPagesShuffled(mirrorIndex: Int) = when (mirrorIndex) {
    0 -> false
    else -> true
}

internal val mangaSlugRegex = Regex("""^/mz[a-z]{4}-""")

internal class Selectors(
    val listMangaSelector: String,
    val detailsSelector: String,
    val recommendClass: String,
)
