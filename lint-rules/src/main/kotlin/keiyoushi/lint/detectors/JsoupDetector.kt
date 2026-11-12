package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import keiyoushi.lint.argumentOf
import keiyoushi.lint.asCall
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import keiyoushi.lint.stringValue
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class JsoupDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("first", "firstOrNull", "trim", "setUrlWithoutDomain", "parse")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "first", "firstOrNull" -> {
                val inner = node.receiver.asCall() ?: return
                if (inner.methodName == "select" && inner.isJsoup() && node.valueArguments.isEmpty()) {
                    val query = inner.valueArguments.firstOrNull()?.sourcePsi?.text ?: return
                    val whole = node.uastParent as? UQualifiedReferenceExpression
                    context.report(
                        SELECT_FIRST,
                        node,
                        context.getLocation(whole ?: node),
                        "Use `selectFirst($query)`",
                    )
                }
            }

            "trim" -> {
                val inner = node.receiver.asCall() ?: return
                if (inner.methodName in TEXT_METHODS && inner.isJsoup() && node.valueArguments.isEmpty()) {
                    context.report(
                        TEXT_TRIM,
                        node,
                        context.loc(node),
                        "Jsoup's `${inner.methodName}()` is already trimmed",
                        LintFix.create().replace().pattern("""(\s*\.\s*trim\(\))""").with("").range(context.getLocation(node.uastParent ?: node)).build(),
                    )
                }
            }

            "setUrlWithoutDomain" -> {
                val arg = node.valueArguments.firstOrNull().asCall() ?: return
                if (arg.methodName == "attr" && arg.isJsoup()) {
                    val name = arg.valueArguments.firstOrNull().stringValue() ?: return
                    if (!name.startsWith("abs:")) {
                        context.report(
                            RELATIVE_HREF,
                            arg,
                            context.getLocation(arg),
                            "Use `absUrl(\"$name\")` (or `attr(\"abs:$name\")`); `setUrlWithoutDomain` can't resolve every relative URL",
                            LintFix.create().replace().text("\"$name\"").with("\"abs:$name\"").build(),
                        )
                    }
                }
            }

            "parse" -> if (method.containingClass?.qualifiedName == "org.jsoup.Jsoup") {
                val arg = node.valueArguments.firstOrNull().asCall() ?: return
                if (arg.methodName == "string" && arg.receiverType?.canonicalText == "okhttp3.ResponseBody") {
                    context.report(
                        JSOUP_PARSE_BODY,
                        node,
                        context.loc(node),
                        "Use `response.asJsoup()`, which streams the body and closes the response",
                    )
                }
            }
        }
    }

    private fun UCallExpression.isJsoup(): Boolean = resolve()?.containingClass?.qualifiedName?.startsWith("org.jsoup.") == true

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
            val value = node.value as? String ?: return
            val call = node.argumentOf() ?: return
            if (call.methodName !in SELECTORS) return
            val match = VOLATILE_CLASS.find(value) ?: return
            context.report(
                VOLATILE_SELECTOR,
                node,
                context.loc(node),
                "`${match.value}` looks like a generated class name that changes on every site build; use a structural selector",
            )
        }
    }

    companion object {
        private val TEXT_METHODS = setOf("text", "ownText")
        private val SELECTORS = setOf("select", "selectFirst", "selectLast", "selectXpath", "selectStream", "expectFirst", "is")

        // CSS modules (`Card_title__jN8og`: a hash with a digit or capital, not BEM's `card__title`),
        // emotion (`css-1x2y3z`), styled-components (`sc-bdVaJa`)
        private val VOLATILE_CLASS = Regex(
            """\b[A-Za-z][A-Za-z0-9]*_[A-Za-z0-9-]+__(?=[A-Za-z0-9_-]{0,4}[A-Z0-9])[A-Za-z0-9_-]{5}\b|\bcss-(?=[a-z0-9]*\d)[a-z0-9]{5,}\b|\bsc-[a-zA-Z]{6}\b""",
        )

        val SELECT_FIRST = issue<JsoupDetector>(
            "KeiSelectFirst",
            "`select(q).first()` instead of `selectFirst(q)`",
            "`selectFirst` stops at the first match instead of collecting every element.",
        )

        val TEXT_TRIM = issue<JsoupDetector>(
            "KeiTextTrim",
            "`text().trim()` is redundant",
            "Jsoup's `text()` and `ownText()` already normalize and trim whitespace.",
        )

        val RELATIVE_HREF = issue<JsoupDetector>(
            "KeiRelativeHref",
            "`setUrlWithoutDomain` with a raw `attr`",
            "`setUrlWithoutDomain` can't resolve every relative URL; pass `absUrl(\"href\")` or `attr(\"abs:href\")`.",
        )

        val JSOUP_PARSE_BODY = issue<JsoupDetector>(
            "KeiJsoupParseBody",
            "`Jsoup.parse(body.string())`",
            "`response.asJsoup()` streams the body, resolves relative links against the response URL and closes it.",
        )

        val VOLATILE_SELECTOR = issue<JsoupDetector>(
            "KeiVolatileSelector",
            "Selector uses a generated class name",
            """
            Auto-generated class names (CSS modules, emotion, styled-components) change whenever the site is \
            rebuilt. Prefer stable structural selectors, ids or data attributes.
            """,
        )

        val ISSUES = listOf(SELECT_FIRST, TEXT_TRIM, RELATIVE_HREF, JSOUP_PARSE_BODY, VOLATILE_SELECTOR)
    }
}
