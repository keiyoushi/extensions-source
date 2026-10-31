package eu.kanade.tachiyomi.multisrc.keyoapp

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParseDate
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

abstract class KeyoappBase :
    KeiSource(),
    ConfigurableSource {

    protected val preferences = getPreferences()

    open val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override val supportsFilterFetching = true

    override val supportRelatedMangasBySearch = true

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("ar", "en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = intl["pref_show_paid_chapter_title"]
            summaryOn = intl["pref_show_paid_chapter_summary_on"]
            summaryOff = intl["pref_show_paid_chapter_summary_off"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    protected val showPaidChapters
        get() = preferences.getBoolean(SHOW_PAID_CHAPTERS_PREF, false)

    open fun Element.isNovel() = select("""[data-type="novel" i], span:matchesOwn((?i)^novel$)""").isNotEmpty()

    protected fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "dropped" -> SManga.CANCELLED
        "paused" -> SManga.ON_HIATUS
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    open fun String.parseDate(): Long = if (this.contains("ago")) {
        this.parseRelativeDate()
    } else {
        dateFormat.tryParseDate(this)
    }

    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val match = RELATIVE_DATE_REGEX.find(this) ?: return 0L

        val relativeDate = when (val d = match.groupValues[1]) {
            "a", "one" -> 1
            else -> d.toIntOrNull() ?: return 0L
        }

        when (match.groupValues[2]) {
            // parse: 30 seconds ago
            "m", "min", "mins", "minute", "minutes" -> now.add(Calendar.MINUTE, -relativeDate)

            // parses: "42 minutes ago"
            "h", "hr", "hrs", "hour", "hours" -> now.add(Calendar.HOUR, -relativeDate)

            // parses: "1 hour ago" and "2 hours ago"
            "d", "day", "days" -> now.add(Calendar.DAY_OF_YEAR, -relativeDate)

            // parses: "2 days ago"
            "w", "wk", "wks", "week", "weeks" -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate)

            // parses: "2 weeks ago"
            "mo", "mos", "month", "months" -> now.add(Calendar.MONTH, -relativeDate)

            // parses: "2 months ago"
            "y", "yr", "yrs", "year", "years" -> now.add(Calendar.YEAR, -relativeDate) // parse: "2 years ago"
        }
        return now.timeInMillis
    }

    companion object {
        protected const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private val RELATIVE_DATE_REGEX = """(a|one|\d+)\s*(\w+)\s+ago""".toRegex()
    }
}
