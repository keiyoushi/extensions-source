package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiMethod
import keiyoushi.lint.HTTP_SOURCE
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Rules about what each `KeiSource` entry point does. */
class FlowDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            val function = node.sourcePsi as? KtNamedFunction ?: return
            if (!function.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD)) return
            val body = function.bodyExpression?.text ?: return
            val anchor = context.getNameLocation(node)

            when (node.name) {
                "getMangaByUrl" -> if (DETAILS_CALL.containsMatchIn(body) && "initialized" !in body && !node.callsInitializingParser()) {
                    context.report(
                        INITIALIZED,
                        node,
                        anchor,
                        "This parses the full details; set `initialized = true` on the result so the app doesn't fetch them again",
                    )
                }

                "getMangasByUrl" -> if (SINGLE_ITEM_PAGE.containsMatchIn(body)) {
                    context.report(
                        SINGLE_MANGA_BY_URL,
                        node,
                        anchor,
                        "This only ever returns one manga; override `getMangaByUrl` instead",
                    )
                }

                "getImageUrl" -> if (body.trimStart('{', ' ', '\n').startsWith("throw ")) {
                    context.report(
                        IMAGE_URL_STUB,
                        node,
                        anchor,
                        "Don't override `getImageUrl` just to throw; it's only called for pages whose `imageUrl` is empty",
                    )
                }

                "getImageRequest" -> if (Regex("""\bGET\(""").containsMatchIn(body) && "super.getImageRequest" !in body) {
                    context.report(
                        IMAGE_REQUEST_FROM_SCRATCH,
                        node,
                        anchor,
                        "Start from `super.getImageRequest(page).newBuilder()`, which keeps `imageFetch` and the default headers",
                    )
                }

                "getPageList" -> if (EMPTY_THROW.containsMatchIn(body)) {
                    context.report(
                        EMPTY_PAGES_THROW,
                        node,
                        anchor,
                        "Return `emptyList()` for a chapter without pages; the app shows a localized error",
                    )
                }
            }
        }
    }

    /** A details parser called here that sets `initialized` itself. */
    private fun UMethod.callsInitializingParser(): Boolean {
        var found = false
        accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val name = node.methodName.orEmpty()
                    // Only source methods: a compiled one (a theme from another module) would need decompiling, which crashes lint
                    val method = node.resolve()?.takeIf { it !is PsiCompiledElement }
                    if (DETAILS_NAME.containsMatchIn(name) && method?.navigationElement?.text?.contains("initialized") == true) found = true
                    return super.visitCallExpression(node)
                }
            },
        )
        return found
    }

    override fun getApplicableMethodNames(): List<String> = listOf("get", "post", "subList", "shuffled", "shuffle", "nextInt", "random")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val listing = node.getParentOfType<UMethod>(true)?.name in LISTINGS
        when (method.name) {
            "get", "post" -> {
                if (method.containingClass?.qualifiedName?.startsWith("keiyoushi.network.") != true) return
                checkDefaultHeaders(context, node)
                if (listing && node.isInsideIteration()) {
                    context.report(
                        REQUEST_PER_ITEM,
                        node,
                        context.loc(node),
                        "A listing page should cost one request; don't fetch per entry (covers come from the details anyway)",
                    )
                }
            }

            "subList" -> if (listing) {
                context.report(
                    CLIENT_SIDE_PAGING,
                    node,
                    context.loc(node),
                    "The whole list is already here; return it in one page with `hasNextPage = false`",
                )
            }

            "shuffled", "shuffle", "nextInt", "random" -> if (node.getParentOfType<UMethod>(true)?.isReachedFromListing() == true) {
                context.report(
                    RANDOM_LISTING,
                    node,
                    context.loc(node),
                    "Listings must be deterministic; use one ordered request",
                )
            }
        }
    }

    private fun checkDefaultHeaders(context: JavaContext, node: UCallExpression) {
        node.valueArguments.forEach { arg ->
            val ref = arg.skipParenthesizedExprDown() as? UReferenceExpression ?: return@forEach
            val resolved = ref.resolve() as? PsiMethod ?: return@forEach
            if (resolved.name == "getHeaders" && context.evaluator.extendsClass(resolved.containingClass, HTTP_SOURCE, false)) {
                context.report(
                    DEFAULT_HEADERS_ARGUMENT,
                    arg,
                    context.getLocation(arg),
                    "The source's `headers` are used by default; drop the argument",
                )
            }
        }
    }

    /** A listing itself, or a method of the same class that a listing calls, directly or not. */
    private fun UMethod.isReachedFromListing(): Boolean {
        if (name in LISTINGS) return true
        val owner = getContainingUClass() ?: return false
        val seen = mutableSetOf<PsiMethod>()
        val queue = ArrayDeque(owner.methods.filter { it.name in LISTINGS })
        while (queue.isNotEmpty()) {
            val method = queue.removeFirst()
            if (!seen.add(method.javaPsi)) continue
            if (method.javaPsi == javaPsi) return true
            method.accept(
                object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val target = node.resolve()?.takeIf { it !is PsiCompiledElement && it.containingClass == owner.javaPsi }
                        target?.toUElementOfType<UMethod>()?.let(queue::add)
                        return super.visitCallExpression(node)
                    }
                },
            )
        }
        return false
    }

    private fun UElement.isInsideIteration(): Boolean {
        var lambda = getParentOfType<ULambdaExpression>(true)
        while (lambda != null) {
            val call = lambda.uastParent as? UCallExpression
            if (call?.methodName in ITERATORS) return true
            lambda = lambda.getParentOfType<ULambdaExpression>(true)
        }
        return false
    }

    companion object {
        private val LISTINGS = setOf("getPopularManga", "getLatestUpdates", "getSearchMangaList")
        private val ITERATORS = setOf("map", "mapNotNull", "mapIndexed", "forEach", "forEachIndexed", "flatMap", "associate", "associateWith")
        private val DETAILS_NAME = Regex("""(?i)details""")
        private val DETAILS_CALL = Regex("""(?i)details\w*\(""")
        private val SINGLE_ITEM_PAGE = Regex("""MangasPage\(\s*(mangas\s*=\s*)?listOf(NotNull)?\(|\bmangas\s*=\s*listOf(NotNull)?\(""")
        private val EMPTY_THROW = Regex("""isEmpty\(\)\)?\s*\{?\s*throw\b|\?:\s*throw\s+\w*Exception\("[^"]*(?i:no pages|not found|pages not)""")

        val INITIALIZED = issue<FlowDetector>(
            "KeiUrlLookupNotInitialized",
            "`getMangaByUrl` result not marked initialized",
            "When `getMangaByUrl` already parsed the full details, set `initialized = true`, or the app fetches them again right away.",
        )

        val SINGLE_MANGA_BY_URL = issue<FlowDetector>(
            "KeiSingleMangaByUrl",
            "`getMangasByUrl` for a single manga",
            "`getMangasByUrl` is for URLs that resolve to several manga; for one, override `getMangaByUrl`.",
        )

        val IMAGE_URL_STUB = issue<FlowDetector>(
            "KeiImageUrlStub",
            "`getImageUrl` overridden only to throw",
            "`getImageUrl` is only called for pages without `imageUrl`; leave it alone when `getPageList` fills every one.",
        )

        val IMAGE_REQUEST_FROM_SCRATCH = issue<FlowDetector>(
            "KeiImageRequestFromScratch",
            "Image request rebuilt from scratch",
            "Customize `super.getImageRequest(page).newBuilder()` so the request keeps `imageFetch` and the source headers.",
        )

        val EMPTY_PAGES_THROW = issue<FlowDetector>(
            "KeiEmptyPagesThrow",
            "Exception thrown for an empty page list",
            "Return `emptyList()` when a chapter has no pages (locked, empty); the app shows a localized error.",
        )

        val REQUEST_PER_ITEM = issue<FlowDetector>(
            "KeiRequestPerListingItem",
            "Request per entry in a listing",
            """
            Popular, latest and search pages should cost one request. Don't fetch covers, page counts or details for \
            each entry; `fetchMangaUpdate` fills them when the entry is opened.
            """,
        )

        val CLIENT_SIDE_PAGING = issue<FlowDetector>(
            "KeiClientSidePaging",
            "Client-side paging of a complete list",
            "When the site returns everything at once, return it all with `hasNextPage = false`; the app handles long lists.",
        )

        val RANDOM_LISTING = issue<FlowDetector>(
            "KeiRandomListing",
            "Random order in a listing",
            "Listings must be deterministic so paging and deduplication work; use one ordered request.",
        )

        val DEFAULT_HEADERS_ARGUMENT = issue<FlowDetector>(
            "KeiDefaultHeadersArgument",
            "Source `headers` passed explicitly",
            "`client.get`/`post` use the source's `headers` when none are passed.",
        )

        val ISSUES = listOf(
            INITIALIZED, SINGLE_MANGA_BY_URL, IMAGE_URL_STUB, IMAGE_REQUEST_FROM_SCRATCH, EMPTY_PAGES_THROW,
            REQUEST_PER_ITEM, CLIENT_SIDE_PAGING, RANDOM_LISTING, DEFAULT_HEADERS_ARGUMENT,
        )
    }
}
