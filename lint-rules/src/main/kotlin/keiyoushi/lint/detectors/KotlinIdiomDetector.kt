package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import keiyoushi.lint.argument
import keiyoushi.lint.asCall
import keiyoushi.lint.inClass
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import keiyoushi.lint.stringValue
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.getParentOfType

class KotlinIdiomDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        "kotlin.text.Regex",
        "java.lang.StringBuilder",
        "eu.kanade.tachiyomi.source.model.Page",
        "java.lang.String",
    )

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        when (constructor.containingClass?.qualifiedName) {
            "kotlin.text.Regex" -> checkRegex(context, node)

            "java.lang.StringBuilder" -> context.report(
                STRING_BUILDER,
                node,
                context.loc(node),
                "Use `buildString { }` (or `MarkdownDescription.Builder` for descriptions)",
            )

            "eu.kanade.tachiyomi.source.model.Page" -> {
                val url = context.argument(node, constructor, "url") ?: return
                if (url.stringValue() == "") {
                    val image = context.argument(node, constructor, "imageUrl")?.sourcePsi?.text ?: return
                    val index = context.argument(node, constructor, "index")?.sourcePsi?.text ?: return
                    context.report(
                        PAGE_EMPTY_URL,
                        node,
                        context.loc(node),
                        "Use `Page($index, imageUrl = $image)`",
                        LintFix.create().replace().range(context.loc(node)).all().with("Page($index, imageUrl = $image)").build(),
                    )
                }
            }

            "java.lang.String" -> if (node.valueArguments.size == 2 && node.valueArguments[1].sourcePsi?.text?.endsWith("UTF_8") == true) {
                context.report(
                    STDLIB_ENCODING,
                    node,
                    context.loc(node),
                    "Use `bytes.decodeToString()`",
                )
            }
        }
    }

    private fun checkRegex(context: JavaContext, node: UCallExpression) {
        val pattern = node.valueArguments.firstOrNull() ?: return
        checkEscapes(context, pattern.sourcePsi as? KtStringTemplateExpression)
        if (pattern.sourcePsi !is KtStringTemplateExpression || (pattern.sourcePsi as KtStringTemplateExpression).hasInterpolation()) return
        if (node.isInsideFunction()) {
            context.report(
                REGEX_IN_FUNCTION,
                node,
                context.loc(node),
                "This `Regex` is compiled on every call; move it to a class, `companion object` or top-level property",
            )
        }
    }

    private fun checkEscapes(context: JavaContext, template: KtStringTemplateExpression?) {
        if (template == null || template.text.startsWith("\"\"\"")) return
        if ("\\\\" in template.text) {
            context.report(
                REGEX_ESCAPING,
                template,
                context.getLocation(template),
                "Use a raw string (`\"\"\"...\"\"\"`) to avoid double escaping",
            )
        }
    }

    /** True inside a function body (getters included), false in property initializers and `lazy` blocks. */
    private fun UElement.isInsideFunction(): Boolean {
        val method = getParentOfType<UMethod>(true) ?: return false
        val lambda = getParentOfType<ULambdaExpression>(true)
        // A lambda inside a property initializer has no method between it and the class
        return lambda == null || lambda.getParentOfType<UMethod>(true) == method
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "toRegex",
        "first", "firstOrNull",
        "joinToString",
        "toByteArray", "toString", "format",
        "getInstance",
        "d", "v", "i", "w", "e",
        "getSharedPreferences",
        "String",
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "toRegex" -> if (method.inClass("kotlin.text.StringsKt", "kotlin.text.StringsKt__RegexExtensionsKt", "kotlin.text.StringsKt__RegexExtensionsJVMKt")) {
                val receiver = node.receiver?.sourcePsi as? KtStringTemplateExpression ?: return
                checkEscapes(context, receiver)
                if (!receiver.hasInterpolation() && node.isInsideFunction()) {
                    context.report(
                        REGEX_IN_FUNCTION,
                        node,
                        context.getLocation(node.uastParent ?: node),
                        "This `Regex` is compiled on every call; move it to a class, `companion object` or top-level property",
                    )
                }
            }

            "first", "firstOrNull" -> {
                val inner = node.receiver.asCall() ?: return
                if (inner.methodName == "filterIsInstance" && node.valueArguments.isEmpty()) {
                    val type = inner.typeArguments.firstOrNull()?.presentableText ?: "T"
                    val helper = if (method.name == "first") "firstInstance" else "firstInstanceOrNull"
                    context.report(
                        FILTER_IS_INSTANCE,
                        node,
                        context.getLocation(node.uastParent ?: node),
                        "Use `$helper<$type>()` from `keiyoushi.utils`",
                    )
                }
            }

            "joinToString" -> {
                val first = node.valueArguments.firstOrNull() ?: return
                if (first.stringValue() == ", " && method.containingClass?.qualifiedName?.startsWith("kotlin.collections.") == true) {
                    context.report(
                        JOIN_TO_STRING_SEPARATOR,
                        first,
                        context.getLocation(first),
                        "`\", \"` is already the default separator of `joinToString`",
                    )
                }
            }

            "toByteArray" -> if (node.valueArguments.firstOrNull()?.sourcePsi?.text?.endsWith("UTF_8") == true || node.valueArguments.isEmpty()) {
                if (node.receiverType?.canonicalText == "java.lang.String") {
                    context.report(
                        STDLIB_ENCODING,
                        node,
                        context.loc(node),
                        "Use `encodeToByteArray()`",
                        LintFix.create().replace().range(context.loc(node)).all().with("encodeToByteArray()").build(),
                    )
                }
            }

            "toString" -> if (node.receiverType?.canonicalText == "byte[]" && node.valueArguments.size == 1) {
                context.report(
                    STDLIB_ENCODING,
                    node,
                    context.loc(node),
                    "Use `decodeToString()`",
                    LintFix.create().replace().range(context.loc(node)).all().with("decodeToString()").build(),
                )
            }

            "format" -> {
                val receiver = node.receiver.stringValue() ?: return
                if (receiver.matches(HEX_FORMAT)) {
                    context.report(
                        STDLIB_ENCODING,
                        node,
                        context.loc(node),
                        "Use the stdlib `ByteArray.toHexString()` instead of formatting each byte",
                    )
                }
            }

            "getInstance" -> if (method.inClass("javax.crypto.Cipher", "java.security.MessageDigest", "javax.crypto.Mac")) {
                context.report(
                    MANUAL_CRYPTO,
                    node,
                    context.loc(node),
                    "Use the `keiyoushi.utils` crypto helpers (`aesDecrypt`/`aesCbc`/`aesGcm`, `cryptoJsDecrypt`, `md5`, `sha256`, `hmacSha256`, `rc4`, `xor`)",
                )
            }

            "d", "v", "i", "w", "e" -> if (method.inClass("android.util.Log")) {
                context.report(
                    DEBUG_LOG,
                    node,
                    context.loc(node),
                    "Remove debug logging before submitting",
                )
            }

            "String" -> if (method.inClass("kotlin.text.StringsKt", "kotlin.text.StringsKt__StringsJVMKt") &&
                node.valueArguments.size == 2 &&
                node.valueArguments[1].sourcePsi?.text?.endsWith("UTF_8") == true
            ) {
                context.report(STDLIB_ENCODING, node, context.loc(node), "Use `bytes.decodeToString()`")
            }

            "getSharedPreferences" -> context.report(
                SHARED_PREFERENCES,
                node,
                context.loc(node),
                "Use `getPreferences()`/`getPreferencesLazy()` from `keiyoushi.utils`",
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        ULiteralExpression::class.java,
        UCatchClause::class.java,
        UThrowExpression::class.java,
        UClass::class.java,
        UQualifiedReferenceExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
            val value = node.value as? String ?: return
            if ("• " in value) {
                context.report(
                    MARKDOWN_BULLET,
                    node,
                    context.loc(node),
                    "Descriptions are Markdown: use `- ` list items, or `MarkdownDescription.Builder`",
                )
            }
        }

        // `Result` is a value class, so its members don't resolve and never reach visitMethodCall
        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            val selector = node.selector as? UCallExpression ?: return
            val name = selector.methodName ?: return
            if (name !in RESULT_FALLBACKS || node.receiver.asCall()?.methodName != "runCatching") return
            context.report(
                RUN_CATCHING,
                node,
                context.loc(node),
                "`runCatching { }.$name` hides the error; let it throw so broken parsing is reported",
            )
        }

        override fun visitCatchClause(node: UCatchClause) {
            val body = node.body as? UBlockExpression ?: return
            if (body.expressions.isEmpty()) {
                context.report(
                    RUN_CATCHING,
                    node,
                    context.loc(node),
                    "Empty `catch` swallows the error; let it throw",
                )
            }
        }

        override fun visitThrowExpression(node: UThrowExpression) {
            val call = node.thrownExpression.asCall() ?: return
            val text = call.valueArguments.firstOrNull()?.sourcePsi?.text ?: return
            if (HTTP_MESSAGE.containsMatchIn(text)) {
                context.report(
                    HTTP_EXCEPTION,
                    node,
                    context.loc(node),
                    "Throw `HttpException(response.code)`, or let `ensureSuccess` throw, so the app can show a localized message",
                )
            }
        }

        override fun visitClass(node: UClass) {
            if (node.isInterface) return
            // Exceptions carrying data are caught and handled inside the source; only flag message-only ones
            val ktClass = node.sourcePsi as? KtClass
            if (ktClass != null && (ktClass.primaryConstructorParameters.any { it.hasValOrVar() } || ktClass.body?.properties?.isNotEmpty() == true)) return
            if (context.evaluator.extendsClass(node.javaPsi, "java.lang.Throwable", true)) {
                context.report(
                    CUSTOM_EXCEPTION,
                    node,
                    context.getLocation(node.uastAnchor ?: node),
                    "Throw `Exception`/`IOException` with a message instead of declaring an exception class",
                )
            }
        }
    }

    companion object {
        private val RESULT_FALLBACKS = setOf("getOrNull", "getOrDefault", "getOrElse")
        private val HEX_FORMAT = Regex("%0?2[xX]")
        private val HTTP_MESSAGE = Regex("""^"HTTP (error )?\$\{?[\w.]*code""", RegexOption.IGNORE_CASE)

        val REGEX_IN_FUNCTION = issue<KotlinIdiomDetector>(
            "KeiRegexInFunction",
            "`Regex` compiled inside a function",
            "Define `Regex` instances at class level, in a `companion object` or at top level so they aren't recompiled on every call.",
        )

        val REGEX_ESCAPING = issue<KotlinIdiomDetector>(
            "KeiRegexEscaping",
            "Double-escaped regex",
            "Write regexes as raw strings (`\"\"\"\\d+\"\"\"`) so backslashes don't need doubling.",
        )

        val FILTER_IS_INSTANCE = issue<KotlinIdiomDetector>(
            "KeiFilterIsInstanceFirst",
            "`filterIsInstance<T>().first()`",
            "Use `firstInstance<T>()`/`firstInstanceOrNull<T>()` from `keiyoushi.utils`.",
        )

        val JOIN_TO_STRING_SEPARATOR = issue<KotlinIdiomDetector>(
            "KeiJoinToStringSeparator",
            "Default `joinToString` separator passed",
            "`joinToString()` already joins with `\", \"`.",
        )

        val STDLIB_ENCODING = issue<KotlinIdiomDetector>(
            "KeiStdlibEncoding",
            "Charset/hex boilerplate instead of the stdlib",
            """
            Use `encodeToByteArray()`, `decodeToString()`, `ByteArray.toHexString()` and `decodeHex()` instead of \
            `toByteArray(Charsets.UTF_8)`, `String(bytes, Charsets.UTF_8)` and `"%02x".format(...)`.
            """,
        )

        val MANUAL_CRYPTO = issue<KotlinIdiomDetector>(
            "KeiManualCrypto",
            "Crypto primitives used by hand",
            """
            `keiyoushi.utils` has `aesDecrypt`/`aesEncrypt` with `aesCbc`/`aesGcm`, `cryptoJsDecrypt`, `md5`, \
            `sha256`, `hmacSha256`, `rc4`, `xor` and `XorInterceptor`, with streaming variants for responses.
            """,
        )

        val RUN_CATCHING = issue<KotlinIdiomDetector>(
            "KeiSwallowedError",
            "Error swallowed",
            """
            `runCatching { }.getOrNull()` and empty `catch` blocks hide broken selectors and API changes. \
            Let parsing fail loudly; use `?.`/`mapNotNull` only for genuinely optional data.
            """,
        )

        val STRING_BUILDER = issue<KotlinIdiomDetector>(
            "KeiStringBuilder",
            "`StringBuilder()` instead of `buildString`",
            "Use `buildString { }`; for manga descriptions use `MarkdownDescription.Builder`.",
        )

        val PAGE_EMPTY_URL = issue<KotlinIdiomDetector>(
            "KeiPageEmptyUrl",
            "`Page(i, \"\", url)`",
            "Use the named parameter: `Page(i, imageUrl = url)`.",
        )

        val DEBUG_LOG = issue<KotlinIdiomDetector>(
            "KeiDebugLog",
            "Debug logging left in",
            "Remove `Log` calls used while debugging.",
        )

        val SHARED_PREFERENCES = issue<KotlinIdiomDetector>(
            "KeiSharedPreferences",
            "SharedPreferences opened by hand",
            "Use `getPreferences()`/`getPreferencesLazy()` from `keiyoushi.utils`, keyed on the source id.",
            severity = Severity.ERROR,
        )

        val MARKDOWN_BULLET = issue<KotlinIdiomDetector>(
            "KeiMarkdownBullet",
            "`•` bullet in a Markdown description",
            "`SManga.description` is Markdown: use `- ` list items, or `MarkdownDescription.Builder`.",
        )

        val HTTP_EXCEPTION = issue<KotlinIdiomDetector>(
            "KeiHttpException",
            "`Exception(\"HTTP \$code\")`",
            "Throw `HttpException(code)` (or keep `ensureSuccess = true`) so the app can show a localized message.",
        )

        val CUSTOM_EXCEPTION = issue<KotlinIdiomDetector>(
            "KeiCustomException",
            "Custom exception class",
            "Extensions throw `Exception`/`IOException` with a short message; custom exception classes add nothing for the app.",
        )

        val ISSUES = listOf(
            REGEX_IN_FUNCTION, REGEX_ESCAPING, FILTER_IS_INSTANCE, JOIN_TO_STRING_SEPARATOR, STDLIB_ENCODING,
            MANUAL_CRYPTO, RUN_CATCHING, STRING_BUILDER, PAGE_EMPTY_URL, DEBUG_LOG, SHARED_PREFERENCES,
            MARKDOWN_BULLET, HTTP_EXCEPTION, CUSTOM_EXCEPTION,
        )
    }
}
