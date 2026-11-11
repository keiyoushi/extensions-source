package keiyoushi.lint.detectors

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import keiyoushi.lint.inClass
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import keiyoushi.lint.stringValue
import org.jetbrains.uast.UCallExpression

class DateTimeDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> = listOf("java.text.SimpleDateFormat")

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        context.report(
            SIMPLE_DATE_FORMAT,
            node,
            context.loc(node),
            "Use a `DateTimeFormatter` with `tryParseDate`/`tryParseDateTime`/`tryParseZonedDateTime`, or `Instant.tryParse` for ISO-8601",
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("currentTimeMillis", "ofPattern")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "currentTimeMillis" -> if (method.inClass("java.lang.System")) {
                context.report(
                    CURRENT_TIME_MILLIS,
                    node,
                    context.loc(node),
                    "Use `kotlin.time` (`Clock.System.now()`, `Duration`) instead of `System.currentTimeMillis()` and millisecond arithmetic",
                )
            }

            "ofPattern" -> if (method.inClass("java.time.format.DateTimeFormatter")) {
                val pattern = node.valueArguments.firstOrNull().stringValue() ?: return
                val unquoted = pattern.replace(QUOTED, "")
                if (node.valueArguments.size < 2 && LOCALE_SENSITIVE.containsMatchIn(unquoted)) {
                    context.report(
                        PATTERN_LOCALE,
                        node,
                        context.loc(node),
                        "`$pattern` contains month/day names or AM/PM; pass the site's `Locale`",
                    )
                }
                if ("'Z'" in pattern) {
                    context.report(
                        QUOTED_OFFSET,
                        node,
                        context.loc(node),
                        "A quoted `'Z'` ignores the offset; use `XXX` with `tryParseZonedDateTime`, or `Instant.tryParse` for ISO-8601",
                    )
                }
            }
        }
    }

    companion object {
        private val QUOTED = Regex("'[^']*'")
        private val LOCALE_SENSITIVE = Regex("MMM|LLL|EEE|ccc|a")

        val SIMPLE_DATE_FORMAT = issue<DateTimeDetector>(
            "KeiSimpleDateFormat",
            "`SimpleDateFormat` in new code",
            """
            Declare a `java.time.format.DateTimeFormatter` at class or file level and use `tryParseDate`, \
            `tryParseDateTime` or `tryParseZonedDateTime`; use `Instant.tryParse` for ISO-8601 instants. \
            They return `0L` on failure, so no `runCatching` is needed.
            """,
        )

        val CURRENT_TIME_MILLIS = issue<DateTimeDetector>(
            "KeiCurrentTimeMillis",
            "`System.currentTimeMillis()` instead of `kotlin.time`",
            "`Clock.System.now()` with `Duration` (`5.minutes`, `1.days`) reads better than milliseconds and magic numbers.",
        )

        val PATTERN_LOCALE = issue<DateTimeDetector>(
            "KeiDatePatternLocale",
            "Locale-sensitive date pattern without a `Locale`",
            "Month and day names depend on the locale; pass the site's `Locale` so parsing doesn't depend on the device.",
        )

        val QUOTED_OFFSET = issue<DateTimeDetector>(
            "KeiQuotedOffset",
            "Quoted `'Z'` in a date pattern",
            "Offset letters must not be quoted; use `XXX`, or `Instant.tryParse` for ISO-8601 instants.",
        )

        val ISSUES = listOf(SIMPLE_DATE_FORMAT, CURRENT_TIME_MILLIS, PATTERN_LOCALE, QUOTED_OFFSET)
    }
}
