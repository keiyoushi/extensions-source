package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import keiyoushi.lint.HTTP_SOURCE
import keiyoushi.lint.argument
import keiyoushi.lint.enclosingMethodName
import keiyoushi.lint.inClass
import keiyoushi.lint.inInterceptor
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import keiyoushi.lint.stringValue
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType

class NetworkDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "newCall",
        "set", "add", "header", "addHeader", "removeAll", "removeHeader",
        "newBuilder",
        "string",
        "toString",
        "makeText",
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            // Interceptors, Dns and other blocking code, and fire-and-forget `enqueue`, have no suspend helper
            "newCall" -> if (method.inClass("okhttp3.OkHttpClient", "okhttp3.Call.Factory") && node.inSuspendFunction(context) && !node.isEnqueued()) {
                context.report(
                    NEW_CALL,
                    node,
                    context.loc(node),
                    "Use `client.get`/`post`/`put`/`head` from `keiyoushi.network`, or `client.call(request)`, instead of `newCall`",
                )
            }

            "set", "add", "header", "addHeader", "removeAll", "removeHeader" ->
                if (method.inClass("okhttp3.Headers.Builder", "okhttp3.Request.Builder")) checkHeader(context, node, method)

            "newBuilder" -> checkHeadersNewBuilder(context, node, method)

            "string" -> if (method.inClass("okhttp3.ResponseBody") && !context.inInterceptor(node)) {
                context.report(
                    BODY_STRING,
                    node,
                    context.loc(node),
                    "Use `response.parseAs<T>()` (optionally with a transform) or `response.asJsoup()` instead of reading the body as a string",
                )
            }

            "toString" -> checkHttpUrlToString(context, node, method)

            "makeText" -> if (method.inClass("android.widget.Toast") && !node.onUiThread()) {
                context.report(
                    TOAST,
                    node,
                    context.loc(node),
                    "A `Toast` from source code runs off the UI thread and won't show; throw an exception with the message instead",
                )
            }
        }
    }

    private fun UCallExpression.inSuspendFunction(context: JavaContext): Boolean {
        val method = getParentOfType<UMethod>(true) ?: return false
        return context.evaluator.isSuspend(method)
    }

    private fun UCallExpression.isEnqueued(): Boolean {
        var parent = uastParent
        while (parent is UQualifiedReferenceExpression) {
            if ((parent.selector as? UCallExpression)?.methodName == "enqueue") return true
            parent = parent.uastParent
        }
        return false
    }

    /** Preference listeners and `post`/`runOnUiThread` blocks run on the UI thread, where a toast does show. */
    private fun UCallExpression.onUiThread(): Boolean {
        if (getParentOfType<UMethod>(true)?.name == "setupPreferenceScreen") return true
        var lambda = getParentOfType<ULambdaExpression>(true)
        while (lambda != null) {
            val name = (lambda.uastParent as? UCallExpression)?.methodName
            if (name != null && (name.startsWith("setOn") || name in UI_THREAD_RUNNERS)) return true
            lambda = lambda.getParentOfType<ULambdaExpression>(true)
        }
        return false
    }

    private fun checkHeader(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = context.argument(node, method, "name").stringValue() ?: return
        val lower = name.lowercase()
        val methodName = node.methodName ?: return

        when {
            lower == "referer" || lower == "origin" -> if (node.enclosingMethodName() == "configureHeaders") {
                if (methodName in SETTERS) {
                    context.report(
                        DEFAULT_REFERER,
                        node,
                        context.loc(node),
                        "`KeiSource` already sets `Referer` to `\$baseUrl/` and `Origin` to `baseUrl`",
                    )
                }
            } else if (lower == "referer" && methodName in SETTERS) {
                context.report(
                    MANUAL_REFERER,
                    node,
                    context.loc(node),
                    "Pass the page the request comes from with `Fetch.X.from(url)` instead of setting `Referer` by hand",
                )
            }

            lower == "cookie" && methodName in SETTERS -> context.report(
                COOKIE_HEADER,
                node,
                context.loc(node),
                "Use `addCookie` in `configureClient()`; a `Cookie` header replaces the cookie jar's cookies, Cloudflare's included",
            )

            lower.startsWith("sec-fetch-") || lower.startsWith("sec-ch-ua") || lower in BROWSER_MANAGED -> context.report(
                BROWSER_HEADER,
                node,
                context.loc(node),
                "`$name` is managed by the browser headers of `Fetch`; pick the right `Fetch` instead of setting it",
            )
        }

        if (methodName in ADDERS && lower in SINGLE_VALUED) {
            context.report(
                HEADER_ADD,
                node,
                context.loc(node),
                "Use `set` for `$name`; `add` keeps any existing value and sends the header twice",
                LintFix.create().replace().text(methodName).with("set").build(),
            )
        }
    }

    private fun checkHeadersNewBuilder(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!method.inClass("okhttp3.Headers")) return
        val receiver = (node.uastParent as? UQualifiedReferenceExpression)?.receiver as? UReferenceExpression ?: return
        val resolved = receiver.resolve() as? PsiMethod ?: return
        if (resolved.name == "getHeaders" && context.evaluator.extendsClass(resolved.containingClass, HTTP_SOURCE, false)) {
            context.report(
                HEADERS_NEW_BUILDER,
                node,
                context.getLocation(node.uastParent ?: node),
                "Start from `headersBuilder()` instead of `headers.newBuilder()`",
            )
        }
    }

    private fun checkHttpUrlToString(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType?.canonicalText ?: return
        if (receiverType != "okhttp3.HttpUrl") return
        val argOf = (node.uastParent as? UQualifiedReferenceExpression)?.uastParent as? UCallExpression
            ?: node.uastParent as? UCallExpression
            ?: return
        val target = argOf.resolve() ?: return
        if (target.containingClass?.qualifiedName?.startsWith("keiyoushi.network.") == true) {
            context.report(
                HTTP_URL_TO_STRING,
                node,
                context.loc(node),
                "The request helpers take an `HttpUrl`; drop `.toString()`",
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
            val value = node.value as? String ?: return
            if (value.startsWith("Mozilla/5.0")) {
                context.report(
                    HARDCODED_USER_AGENT,
                    node,
                    context.loc(node),
                    "Don't hardcode a `User-Agent`; the app's one is used by default. If a desktop one is really needed, derive it from the app's Chrome version",
                )
            }
            if (value.contains("Just a moment")) {
                context.report(
                    CLOUDFLARE_CHECK,
                    node,
                    context.loc(node),
                    "Don't check for Cloudflare challenges; the app handles them before the parser runs",
                )
            }
        }
    }

    companion object {
        private val UI_THREAD_RUNNERS = setOf("post", "postDelayed", "runOnUiThread", "launchUI")
        private val SETTERS = setOf("set", "add", "header", "addHeader")
        private val ADDERS = setOf("add", "addHeader")
        private val SINGLE_VALUED = setOf(
            "user-agent",
            "referer",
            "origin",
            "accept",
            "authorization",
            "content-type",
            "x-requested-with",
        )
        private val BROWSER_MANAGED = setOf("accept-language", "priority", "cache-control", "upgrade-insecure-requests")

        val NEW_CALL = issue<NetworkDetector>(
            "KeiNewCall",
            "Request sent with `newCall`",
            """
            Requests go through the `keiyoushi.network` suspend helpers `client.get`/`post`/`put`/`head`, \
            which take a `Fetch` and close nothing by surprise. For anything else, build the `Request` and send \
            it with `client.call(request)`.
            """,
        )

        val MANUAL_REFERER = issue<NetworkDetector>(
            "KeiManualReferer",
            "`Referer` set by hand",
            """
            Use `Fetch.X.from(url)` (for example `Fetch.IMAGE.from(getChapterUrl(chapter))`) to say which page a \
            request comes from. It sends the `Referer` the way browsers do, including on redirects.
            """,
        )

        val DEFAULT_REFERER = issue<NetworkDetector>(
            "KeiDefaultReferer",
            "`Referer`/`Origin` restated in `configureHeaders`",
            "`KeiSource.headersBuilder()` already sets `Referer` to `\"\$baseUrl/\"` and `Origin` to `baseUrl`.",
        )

        val COOKIE_HEADER = issue<NetworkDetector>(
            "KeiCookieHeader",
            "`Cookie` header set by hand",
            """
            Setting the `Cookie` header replaces every cookie of the cookie jar, including the Cloudflare ones \
            set through WebView. Use `addCookie(...)` in `configureClient()`.
            """,
        )

        val BROWSER_HEADER = issue<NetworkDetector>(
            "KeiBrowserManagedHeader",
            "Header managed by `Fetch`",
            """
            Client hints, fetch metadata, `Accept-Language`, `Priority` and `Cache-Control` are sent by the browser \
            headers of the request's `Fetch`. Pick the right `Fetch` instead of setting or removing them.
            """,
        )

        val HEADER_ADD = issue<NetworkDetector>(
            "KeiHeaderAdd",
            "`add` used for a single-valued header",
            "`add` keeps the existing value, so the header goes out twice. Use `set`.",
        )

        val HEADERS_NEW_BUILDER = issue<NetworkDetector>(
            "KeiHeadersNewBuilder",
            "`headers.newBuilder()` in a source",
            "Build derived header sets from `headersBuilder()`, which follows the current `baseUrl`.",
        )

        val BODY_STRING = issue<NetworkDetector>(
            "KeiBodyString",
            "Response body read as a string",
            """
            `response.parseAs<T>()` streams and closes the body; `parseAs<T> { transform }` covers JSONP or prefixes. \
            For HTML, use `response.asJsoup()`. Reading the body as a string is only for interceptors.
            """,
        )

        val HTTP_URL_TO_STRING = issue<NetworkDetector>(
            "KeiHttpUrlToString",
            "`HttpUrl.toString()` passed to a request helper",
            "`client.get`/`post` accept an `HttpUrl` directly.",
        )

        val TOAST = issue<NetworkDetector>(
            "KeiToast",
            "`Toast` from source code",
            "Source code doesn't run on the UI thread, so the toast never shows. Throw with the message instead.",
        )

        val HARDCODED_USER_AGENT = issue<NetworkDetector>(
            "KeiHardcodedUserAgent",
            "Hardcoded `User-Agent`",
            """
            The app's `User-Agent` is used by default and kept current. Only override it when the site needs it, \
            and derive a desktop one from the app's Chrome version rather than hardcoding a string.
            """,
        )

        val CLOUDFLARE_CHECK = issue<NetworkDetector>(
            "KeiCloudflareCheck",
            "Manual Cloudflare check",
            "The app solves Cloudflare challenges before the response reaches the source.",
        )

        val ISSUES = listOf(
            NEW_CALL, MANUAL_REFERER, DEFAULT_REFERER, COOKIE_HEADER, BROWSER_HEADER, HEADER_ADD,
            HEADERS_NEW_BUILDER, BODY_STRING, HTTP_URL_TO_STRING, TOAST, HARDCODED_USER_AGENT, CLOUDFLARE_CHECK,
        )
    }
}
