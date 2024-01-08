package eu.kanade.tachiyomi.extension.all.pixiv
import eu.kanade.tachiyomi.source.model.Filter

private val TYPE_VALUES = arrayOf("All", "Illustrations", "Manga")
private val TYPE_PARAMS = arrayOf(null, "illust", "manga")

private val TAGS_MODE_VALUES = arrayOf("Partial", "Full")
private val TAGS_MODE_PARAMS = arrayOf("s_tag", "s_tag_full")

private val RATING_VALUES = arrayOf("All", "All ages", "R-18")
private val RATING_PARAMS = arrayOf(null, "all", "r18")

private val RATING_PREDICATES: Array<((PixivIllust) -> Boolean)?> =
    arrayOf(null, { it.x_restrict == "0" }, { it.x_restrict == "1" })

internal class PixivFilters : MutableList<Filter<*>> by mutableListOf() {
    private val typeFilter = object : Filter.Select<String>("Type", TYPE_VALUES, 2) {}.also(::add)
    private val tagsFilter = object : Filter.Text("Tags") {}.also(::add)
    private val tagsModeFilter = object : Filter.Select<String>("Tags mode", TAGS_MODE_VALUES, 0) {}.also(::add)
    private val usersFilter = object : Filter.Text("Users") {}.also(::add)
    private val ratingFilter = object : Filter.Select<String>("Rating", RATING_VALUES, 0) {}.also(::add)

    init { add(Filter.Header("(the following are ignored when the users filter is in use)")) }

    private val orderFilter = object : Filter.Sort("Order", arrayOf("Date posted")) {}.also(::add)
    private val dateBeforeFilter = object : Filter.Text("Posted before") {}.also(::add)
    private val dateAfterFilter = object : Filter.Text("Posted after") {}.also(::add)

    val type: String? get() = TYPE_PARAMS[typeFilter.state]

    val tags: String by tagsFilter::state
    val searchMode: String get() = TAGS_MODE_PARAMS[tagsModeFilter.state]

    fun makeTagsPredicate(): ((PixivIllust) -> Boolean)? {
        val tags = tags.ifBlank { return null }.split(' ')

        if (tagsModeFilter.state == 0) {
            val regex = Regex(tags.joinToString("|") { Regex.escape(it) })
            return { it.tags?.any(regex::containsMatchIn) == true }
        } else {
            return { it.tags?.containsAll(tags) == true }
        }
    }

    val users: String by usersFilter::state

    fun makeUsersPredicate(): ((PixivIllust) -> Boolean)? {
        val users = users.ifBlank { return null }
        val regex = Regex(users.split(' ').joinToString("|") { Regex.escape(it) })

        return { it.author_details?.user_name?.contains(regex) == true }
    }

    val rating: String? get() = RATING_PARAMS[ratingFilter.state]
    fun makeRatingPredicate() = RATING_PREDICATES[ratingFilter.state]

    val order: String? get() = orderFilter.state?.ascending?.let { "date" }

    val dateBefore: String by dateBeforeFilter::state
    val dateAfter: String by dateAfterFilter::state
}
