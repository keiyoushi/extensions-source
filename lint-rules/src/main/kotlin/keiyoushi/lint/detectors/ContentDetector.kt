package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import keiyoushi.lint.SCHAPTER
import keiyoushi.lint.SMANGA
import keiyoushi.lint.asCall
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import keiyoushi.lint.stringValue
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.tryResolve

/** Stored URLs, descriptions and other values the app or reviewers expect in a given shape. */
class ContentDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UBinaryExpression::class.java,
        ULiteralExpression::class.java,
        UClass::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitBinaryExpression(node: UBinaryExpression) {
            when (node.operator) {
                UastBinaryOperator.ASSIGN -> checkAssignment(context, node)
                UastBinaryOperator.IDENTITY_EQUALS, UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS, UastBinaryOperator.IDENTITY_NOT_EQUALS,
                -> checkHostComparison(context, node)
                else -> {}
            }
        }

        override fun visitLiteralExpression(node: ULiteralExpression) {
            val value = node.value as? String ?: return
            when {
                MARKDOWN_LABEL.containsMatchIn(value) -> context.report(
                    MANUAL_DESCRIPTION,
                    node,
                    context.getLocation(node),
                    "Build descriptions with `MarkdownDescription.Builder` (`field`, `info`, `altTitles`, `links`) instead of hand-written Markdown",
                )
                value == "show_nsfw_source" -> context.report(
                    APP_PREFERENCE,
                    node,
                    context.getLocation(node),
                    "Don't read the app's own preferences; add a preference to the extension",
                )
                "self.__next_f" in value -> context.report(
                    NEXT_FLIGHT_REGEX,
                    node,
                    context.getLocation(node),
                    "Use `extractNextJs` instead of parsing the Next.js flight data by hand",
                )
            }
        }

        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (SOLVER_NAME.containsMatchIn(name)) {
                context.report(
                    CUSTOM_TURNSTILE,
                    node,
                    context.getNameLocation(node),
                    "Use `keiyoushi.utils.getTurnstileToken` instead of a custom solver",
                )
            }
        }
    }

    private fun checkAssignment(context: JavaContext, node: UBinaryExpression) {
        val target = node.leftOperand.tryResolve() as? PsiMember ?: return
        val owner = target.containingClass?.qualifiedName ?: return
        if (owner != SMANGA && owner != SCHAPTER) return
        val field = target.name?.removePrefix("set")?.replaceFirstChar { it.lowercase() } ?: return
        val value = node.rightOperand.sourcePsi?.text ?: return

        if (field == "url") {
            val call = node.rightOperand.asCall()
            val absolute = call?.methodName == "absUrl" ||
                (call?.methodName == "attr" && call.valueArguments.firstOrNull().stringValue()?.startsWith("abs:") == true) ||
                ABSOLUTE.containsMatchIn(value)
            if (absolute) {
                context.report(
                    ABSOLUTE_URL,
                    node,
                    context.getLocation(node),
                    "Store the ID/slug or a relative path (`setUrlWithoutDomain`), not an absolute URL",
                )
            }
            if (FRAGMENT.containsMatchIn(value)) {
                context.report(
                    URL_FRAGMENT,
                    node,
                    context.getLocation(node),
                    "Keep extra data in `memo`, not in a URL fragment",
                )
            }
        }

        if (owner == SCHAPTER && field == "name" && TITLE_STRIP.containsMatchIn(value)) {
            context.report(
                STRIP_MANGA_TITLE,
                node,
                context.getLocation(node),
                "The app already removes the manga title from chapter names",
            )
        }
    }

    private fun checkHostComparison(context: JavaContext, node: UBinaryExpression) {
        val sides = listOf(node.leftOperand, node.rightOperand)
        val literal = sides.firstNotNullOfOrNull { it.stringValue() } ?: return
        val other = sides.firstOrNull { it.stringValue() == null }?.sourcePsi?.text ?: return
        if (other.endsWith(".host") && '.' in literal) {
            context.report(
                HARDCODED_HOST,
                node,
                context.getLocation(node),
                "Compare against `baseUrl.toHttpUrl().host` so mirrors and custom URLs keep working",
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("substringAfterLast", "substringAfter", "substringBefore", "text")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "text" -> {
                val inner = node.receiver.asCall() ?: return
                if (inner.methodName == "parseBodyFragment" || (inner.methodName == "parse" && inner.receiver?.sourcePsi?.text == "Jsoup")) {
                    context.report(
                        COLLAPSED_TEXT,
                        node,
                        context.loc(node),
                        "`text()` collapses the paragraphs; use `toMarkdown()` (or `MarkdownDescription.Builder().description(...)`) or `wholeText()`",
                    )
                }
            }

            else -> {
                val separator = node.valueArguments.firstOrNull().stringValue() ?: return
                if (separator != "/" && separator != "?") return
                val receiver = node.receiver?.sourcePsi?.text ?: return
                if (URL_RECEIVER.containsMatchIn(receiver)) {
                    context.report(
                        URL_STRING_SPLIT,
                        node,
                        context.loc(node),
                        "Parse the URL (`toHttpUrl().pathSegments`, `queryParameter`) instead of cutting the string",
                    )
                }
            }
        }
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf("java.util.zip.ZipInputStream")

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        context.report(
            ZIP_INPUT_STREAM,
            node,
            context.loc(node),
            "Use `client.zipDirectory`/`readZipEntry` from `keiyoushi.zip`, which read single entries with Range requests",
        )
    }

    companion object {
        private val MARKDOWN_LABEL = Regex("""^\*\*[^*\n]{1,40}:?\*\*:?\s""")
        private val ABSOLUTE = Regex("""^"(https?://|\$\{?baseUrl\b)""")
        private val FRAGMENT = Regex("""^"[^"]*#""")
        private val TITLE_STRIP = Regex("""removePrefix\([^)]*[Tt]itle""")
        private val URL_RECEIVER = Regex("""(?i)(url|href)\)?$""")
        private val SOLVER_NAME = Regex("""Turnstile\w*(Solver|Helper|Interceptor|Bypass)""")

        val ABSOLUTE_URL = issue<ContentDetector>(
            "KeiAbsoluteStoredUrl",
            "Absolute URL stored in `SManga.url`/`SChapter.url`",
            "Store the ID/slug, or a relative path with `setUrlWithoutDomain`, so domain changes don't break libraries.",
        )

        val URL_FRAGMENT = issue<ContentDetector>(
            "KeiStoredUrlFragment",
            "Data hidden in a URL fragment",
            "Use `memo` on `SManga`/`SChapter` for extra data; fragments are only for `Page.url`.",
        )

        val STRIP_MANGA_TITLE = issue<ContentDetector>(
            "KeiStripMangaTitle",
            "Manga title removed from chapter names",
            "The app strips the manga title from chapter names itself.",
        )

        val HARDCODED_HOST = issue<ContentDetector>(
            "KeiHardcodedHost",
            "Host compared against a literal",
            "Compare hosts against the source's `baseUrl` so mirrors and custom URLs keep working.",
        )

        val URL_STRING_SPLIT = issue<ContentDetector>(
            "KeiUrlStringSplit",
            "URL cut with string functions",
            "Use `HttpUrl.pathSegments`/`queryParameter` to extract IDs and slugs.",
        )

        val COLLAPSED_TEXT = issue<ContentDetector>(
            "KeiCollapsedText",
            "`text()` of an HTML fragment",
            "`text()` joins paragraphs into one line; use `toMarkdown()`, the Markdown builder, or `wholeText()`.",
        )

        val MANUAL_DESCRIPTION = issue<ContentDetector>(
            "KeiManualDescription",
            "Description Markdown written by hand",
            "Use `MarkdownDescription.Builder` (`rating`, `info`, `field`, `altTitles`, `links`); improve the builder if something is missing.",
        )

        val APP_PREFERENCE = issue<ContentDetector>(
            "KeiAppPreference",
            "App preference read by the extension",
            "Extensions keep their own preferences; don't depend on the host app's.",
        )

        val NEXT_FLIGHT_REGEX = issue<ContentDetector>(
            "KeiNextFlightByHand",
            "Next.js flight data parsed by hand",
            "`extractNextJs`/`extractNextJsRsc` parse the hydration payload; hand-written regexes also stack-overflow on large pages.",
        )

        val CUSTOM_TURNSTILE = issue<ContentDetector>(
            "KeiCustomTurnstile",
            "Custom Turnstile/captcha solver",
            "Use `keiyoushi.utils.getTurnstileToken`.",
        )

        val ZIP_INPUT_STREAM = issue<ContentDetector>(
            "KeiZipInputStream",
            "Whole ZIP downloaded into a `ZipInputStream`",
            "`client.zipDirectory` and `client.readZipEntry` read the central directory and single entries with Range requests.",
        )

        val ISSUES = listOf(
            ABSOLUTE_URL, URL_FRAGMENT, STRIP_MANGA_TITLE, HARDCODED_HOST, URL_STRING_SPLIT, COLLAPSED_TEXT,
            MANUAL_DESCRIPTION, APP_PREFERENCE, NEXT_FLIGHT_REGEX, CUSTOM_TURNSTILE, ZIP_INPUT_STREAM,
        )
    }
}
