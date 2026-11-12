package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import keiyoushi.lint.HTTP_SOURCE
import keiyoushi.lint.KEI_SOURCE
import keiyoushi.lint.SCHAPTER
import keiyoushi.lint.SMANGA
import keiyoushi.lint.hasDynamicBaseUrl
import keiyoushi.lint.isTheme
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

class SourceDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UClass::class.java,
        UBinaryExpression::class.java,
        UIfExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val ktClass = node.sourcePsi as? KtClassOrObject ?: return
            if (ktClass is KtObjectDeclaration) return
            if (!context.evaluator.extendsClass(node.javaPsi, HTTP_SOURCE, false)) return

            ktClass.body?.properties.orEmpty().forEach { property -> checkClassProperty(context, node, property) }
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            if (node.operator == UastBinaryOperator.ASSIGN) {
                checkMandatoryField(context, node)
                return
            }
            if (node.operator == UastBinaryOperator.LOGICAL_AND) {
                val text = node.sourcePsi?.text?.replace(WHITESPACE, "") ?: return
                if (text == "!fetchDetails&&!fetchChapters" || text == "!fetchChapters&&!fetchDetails") {
                    context.report(
                        IMPOSSIBLE_FLAGS,
                        node,
                        context.loc(node),
                        "`fetchMangaUpdate` is never called with both flags false",
                    )
                }
            }
        }

        override fun visitIfExpression(node: UIfExpression) {
            val method = node.getParentOfType<UMethod>(true) ?: return
            if (method.name != "fetchMangaUpdate") return
            val ifPsi = node.sourcePsi as? KtIfExpression ?: return
            val condition = ifPsi.condition?.text ?: return
            if (condition.trim() != "fetchChapters") return
            val elseText = ifPsi.`else`?.text?.replace(WHITESPACE, "") ?: return
            if (elseText == "emptyList()" || elseText == "listOf()") {
                context.report(
                    EMPTY_CHAPTERS,
                    node,
                    context.getLocation(ifPsi.`else`!!),
                    "Return the passed `chapters` when `fetchChapters` is false; an empty list can wipe the chapter list",
                )
            }
        }
    }

    private fun checkClassProperty(context: JavaContext, cls: UClass, property: KtProperty) {
        val name = property.name ?: return

        if (name == "supportsLatest" && property.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD)) {
            val value = (property.initializer ?: property.getter?.bodyExpression)?.text
            val superClass = cls.javaPsi.superClass?.qualifiedName
            if (value == "true" && superClass == KEI_SOURCE) {
                context.report(
                    DEFAULT_OVERRIDE,
                    property,
                    context.getLocation(property),
                    "`supportsLatest` is already `true` on `KeiSource`",
                    LintFix.create().replace().range(context.getLocation(property)).all().with("").reformat(true).build(),
                )
            }
        }

        if (property.getter != null || property.isLocal) return
        // A static baseUrl is a constant getter, so only mirrors/custom URLs, fetchBaseUrls and themes can change it
        val overridesFetch = (cls.sourcePsi as? KtClassOrObject)?.body?.functions?.any { it.name == "fetchBaseUrls" } == true
        if (!context.isTheme() && !context.hasDynamicBaseUrl() && !overridesFetch) return
        val initializer = (property.initializer ?: property.delegateExpression).toUElement() ?: return
        val isLazy = property.delegateExpression != null
        val reference = context.eagerReference(initializer, isLazy) ?: return
        context.report(
            STORED_BASE_URL,
            property,
            context.getLocation(property.nameIdentifier ?: property),
            "`$name` is computed once from `$reference`${if (isLazy) " by `lazy`" else ""}; `baseUrl` can change at runtime " +
                "(mirrors, custom URL, `fetchBaseUrls`), so make it a `get()` property",
        )
    }

    /** The first `baseUrl`/`headers` read while [root] runs, skipping lambdas and objects that are only stored. */
    private fun JavaContext.eagerReference(root: UElement, delegated: Boolean): String? {
        var found: String? = null
        root.accept(
            object : AbstractUastVisitor() {
                override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                    if (found == null && node.identifier in DYNAMIC_NAMES && !isDeferred(node, root, delegated)) found = node.identifier
                    return false
                }
            },
        )
        return found
    }

    // A lambda runs right away when an inline function (`run`, `let`, `buildString`) or the `lazy` delegate takes it
    private fun JavaContext.isDeferred(node: UElement, root: UElement, delegated: Boolean): Boolean {
        var element = node.uastParent
        while (element != null) {
            when (element) {
                is UMethod, is UClass -> return true
                is ULambdaExpression -> {
                    val call = element.uastParent as? UCallExpression ?: return true
                    val inline = call.methodName in STDLIB_INLINE || evaluator.isInline(call.resolve())
                    val runsNow = inline || (delegated && call.sourcePsi == root.sourcePsi)
                    if (!runsNow) return true
                }
            }
            if (element.sourcePsi == root.sourcePsi) return false
            element = element.uastParent
        }
        return false
    }

    private fun checkMandatoryField(context: JavaContext, node: UBinaryExpression) {
        val target = node.leftOperand.tryResolve() as? PsiMember ?: return
        val owner = target.containingClass?.qualifiedName ?: return
        val field = target.name?.removePrefix("set")?.replaceFirstChar { it.lowercase() } ?: return
        val mandatory = when (owner) {
            SMANGA -> field == "title" || field == "url"
            SCHAPTER -> field == "name" || field == "url"
            else -> false
        }
        if (!mandatory) return
        val value = node.rightOperand.sourcePsi?.text ?: return
        if (EMPTY_FALLBACK.containsMatchIn(value)) {
            context.report(
                MANDATORY_FALLBACK,
                node,
                context.loc(node),
                "`$field` is mandatory: fail (`!!`, throw, or skip the entry) instead of falling back to a placeholder",
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("sortedByDescending", "sortedBy", "sortByDescending", "sortBy")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val lambda = node.valueArguments.lastOrNull()?.sourcePsi?.text ?: return
        if ("chapter_number" in lambda || "chapterNumber" in lambda) {
            context.report(
                CHAPTER_SORT,
                node,
                context.loc(node),
                "Return chapters in the site's order (`reversed()` if needed) instead of sorting by number",
            )
        }
    }

    companion object {
        private val WHITESPACE = Regex("""\s+""")
        private val DYNAMIC_NAMES = setOf("baseUrl", "headers")

        // The inline modifier of compiled stdlib functions doesn't always resolve
        private val STDLIB_INLINE = setOf(
            "run", "let", "also", "apply", "with", "takeIf", "takeUnless",
            "buildString", "buildList", "buildMap", "buildSet",
            "map", "mapNotNull", "filter", "associate", "associateWith", "flatMap", "forEach",
        )
        private val EMPTY_FALLBACK = Regex("""\?:\s*"(|Untitled|Unknown|No title|Chapter|Sem título|Sin título)"\s*$|\.orEmpty\(\)\s*$""", RegexOption.IGNORE_CASE)

        val DEFAULT_OVERRIDE = issue<SourceDetector>(
            "KeiDefaultOverride",
            "Override restates the default",
            "`KeiSource.supportsLatest` is already `true`.",
        )

        val IMPOSSIBLE_FLAGS = issue<SourceDetector>(
            "KeiImpossibleFlags",
            "Guard for both `fetchMangaUpdate` flags being false",
            "The app never calls `fetchMangaUpdate` with both `fetchDetails` and `fetchChapters` false.",
        )

        val EMPTY_CHAPTERS = issue<SourceDetector>(
            "KeiEmptyChaptersWhenSkipped",
            "Empty chapter list returned when `fetchChapters` is false",
            """
            When chapters aren't fetched, return the `chapters` passed in: the app may apply the returned list \
            regardless of the flags. If details and chapters come from the same response, return both unconditionally.
            """,
        )

        val STORED_BASE_URL = issue<SourceDetector>(
            "KeiStoredBaseUrl",
            "Value derived from `baseUrl`/`headers` stored once",
            """
            `baseUrl` can change while the source is alive (mirror and custom-URL preferences, `fetchBaseUrls`). \
            Properties derived from it, or from `headers`, must be `get()` properties or functions, not stored \
            values or `lazy` delegates. Interceptors that need it should take a `{ baseUrl }` lambda.
            """,
        )

        val MANDATORY_FALLBACK = issue<SourceDetector>(
            "KeiMandatoryFallback",
            "Placeholder for a mandatory field",
            """
            `SManga.title`, `SManga.url`, `SChapter.name` and `SChapter.url` are mandatory. Fail loudly (`!!`, \
            throw) or skip the entry with `mapNotNull` instead of falling back to `""` or a generic placeholder.
            """,
        )

        val CHAPTER_SORT = issue<SourceDetector>(
            "KeiChapterSort",
            "Chapters sorted by number",
            "Return chapters in the site's order, newest first (`reversed()` if the site lists them oldest first).",
        )

        val ISSUES = listOf(
            DEFAULT_OVERRIDE,
            IMPOSSIBLE_FLAGS,
            EMPTY_CHAPTERS,
            STORED_BASE_URL,
            MANDATORY_FALLBACK,
            CHAPTER_SORT,
        )
    }
}
