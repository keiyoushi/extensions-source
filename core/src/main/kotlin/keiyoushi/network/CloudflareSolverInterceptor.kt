package keiyoushi.network

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.ifEmpty

internal class CloudflareSolverInterceptor(
    private val client: OkHttpClient,
    private val cloudflareInterceptor: Interceptor,
) : Interceptor {
    private val application by injectLazy<Application>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.header("cf-mitigated") != "challenge") {
            return response
        }

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var webView: WebView? = null

        handler.post {
            Toast.makeText(application, "Attempting to solve Cloudflare challenge", Toast.LENGTH_SHORT).show()

            val view = WebView(application)
            webView = view

            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                blockNetworkImage = false
                userAgentString = request.header("User-Agent")
            }

            val challengeCompleted = AtomicBoolean(false)

            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    webResourceRequest: WebResourceRequest,
                ): WebResourceResponse? = run {
                    val requestUrl = webResourceRequest.url?.toString()?.toHttpUrlOrNull()
                        ?: return super.shouldInterceptRequest(view, webResourceRequest)

                    when (webResourceRequest.method) {
                        "GET" if requestUrl.toString().startsWith("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/") -> {
                            client
                                .newCall(webResourceRequest.toRequest())
                                .execute()
                                .injectJS(INNER_SCRIPT)
                                .toWebResourceResponse()
                        }

                        "POST" if requestUrl.host == request.url.host &&
                            requestUrl.encodedPath == request.url.encodedPath -> {
                            challengeCompleted.set(true)
                            super.shouldInterceptRequest(view, webResourceRequest)
                        }

                        else -> super.shouldInterceptRequest(view, webResourceRequest)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (challengeCompleted.get()) {
                        latch.countDown()
                    }
                    super.onPageFinished(view, url)
                }
            }

            // Somewhat useful if you need to debug WebView issues. Don't delete.
            /*view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage == null) {
                        return false
                    }
                    val logContent = "wv: ${consoleMessage.message()} (${consoleMessage.sourceId()}, line ${consoleMessage.lineNumber()})"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.DEBUG -> Log.d("cloudflare", logContent)
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("cloudflare", logContent)
                        ConsoleMessage.MessageLevel.LOG -> Log.i("cloudflare", logContent)
                        ConsoleMessage.MessageLevel.TIP -> Log.i("cloudflare", logContent)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("cloudflare", logContent)
                        else -> Log.d("cloudflare", logContent)
                    }

                    return true
                }
            }*/

            view.loadDataWithBaseURL(
                request.url.toString(),
                response.body.string().injectJS(OUTER_SCRIPT),
                "text/html",
                "UTF-8",
                null,
            )
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }
        response.close()

        // Use the original Cloudflare interceptor in case the solver failed
        return cloudflareInterceptor.intercept(chain)
    }

    private fun WebResourceRequest.toRequest(): Request = Request.Builder().apply {
        url(url.toString())
        method(method, null)
        headers(requestHeaders.toHeaders())
    }.build()

    private fun Response.toWebResourceResponse(): WebResourceResponse = WebResourceResponse(
        body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "text/html",
        body.contentType()?.charset(StandardCharsets.UTF_8)?.name() ?: "UTF-8",
        code,
        message.ifEmpty { "OK" },
        headers.toMap()
            .filterKeys { !it.equals("Content-Encoding", true) && !it.equals("Content-Length", true) },
        body.byteStream(),
    )

    /**
     * Returns a new HTML string with the injected JavaScript code.
     *
     * The injected script element is prepended to the HTML, and all `Error` classes are patched so that the injected code doesn't appear in
     * stack traces and that the line numbers correspond to the original unpatched HTML.
     */
    private fun String.injectJS(js: String, nonce: String = ""): String = "<script nonce=\"$nonce\">document.currentScript.remove();(()=>{$js;$ERROR_PATCHER_SCRIPT;errorPatcher(${
        BASE_LINE_COUNT + js.count { it == '\n' }
    });})();</script>\n$this"

    /**
     * Returns a new response with the injected JavaScript code.
     */
    private fun Response.injectJS(js: String): Response = newBuilder().body(
        body.contentType().let { contentType ->
            body
                .string()
                .injectJS(
                    js,
                    header("Content-Security-Policy")?.let { nonceRegex.find(it) }?.value.orEmpty(),
                )
                .toResponseBody(contentType)
        },
    ).build()

    companion object {
        private val nonceRegex = """(?<=nonce-)\w+""".toRegex()

        /**
         * This script runs in the main frame when a Cloudflare challenge is present.
         *
         * This script patches the `postMessage` function of the challenge iframe's content window to use `*` as the target origin.
         */
        private val OUTER_SCRIPT = """
            const contentWindowToProxy = new WeakMap();

            const contentWindowDescriptor = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, "contentWindow");

            function createContentWindowProxy(result) {
              let proxy = contentWindowToProxy.get(result);

              if (proxy) {
                return proxy;
              }

              function postMessage(message, targetOrigin, transfer) {
                result.postMessage(message, targetOrigin === "https://challenges.cloudflare.com" ? "*" : targetOrigin, transfer);
              }

              proxy = new Proxy(result, {
                get(target, prop) {
                  const result = Reflect.get(target, prop);
                  if (prop === "postMessage") {
                    return postMessage;
                  }
                  if (typeof result === "function") {
                    return (...args) => target[prop](...args);
                  }
                  return result;
                }
              });

              contentWindowToProxy.set(result, proxy);

              return proxy;
            }

            addEventListener = (type, listener, options) => {
              if (type === "message") {
                return Window.prototype.addEventListener.call(window, type, e => {
                  let source = e.source;
                  return listener(new Proxy(e, {
                    get(target, prop, receiver) {
                      if (prop === "source") {
                        return createContentWindowProxy(target.source);
                      } else {
                        return target[prop];
                      }
                    }
                  }));
                }, options);
              } else {
                return Window.prototype.addEventListener.call(window, type, listener, options);
              }
            };

            Object.defineProperty(HTMLIFrameElement.prototype, "contentWindow", Object.assign({}, contentWindowDescriptor, {
              get(...args) {
                const result = contentWindowDescriptor.get.apply(this, args);
                return this.src?.startsWith("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/")
                  ? createContentWindowProxy(result)
                  : result;
              }
            }));
        """.trimIndent()

        /**
         * This script runs in the Cloudflare challenge iframe.
         *
         * This script simulates a mouse click on the checkbox.
         */
        private val INNER_SCRIPT = $$"""
            async function simulateMouseClick(element, clientX = null, clientY = null) {
              if (clientX === null || clientY === null) {
                const box = element.getBoundingClientRect();
                clientX = box.left + box.width / 2;
                clientY = box.top + box.height / 2;
              }

              if (isNaN(clientX) || isNaN(clientY)) {
                return;
              }

              // Send mouseover, mousedown, mouseup, click, mouseout
              for (const eventName of [
                "mouseover",
                "mouseenter",
                "mousedown",
                "mouseup",
                "click",
                "mouseout"
              ]) {
                const event = new MouseEvent(eventName, {
                  detail: 1 - (eventName === "mouseover"),
                  bubbles: true,
                  cancelable: true,
                  clientX: clientX,
                  clientY: clientY,
                });
                element.dispatchEvent(event);
                await new Promise(resolve => setTimeout(resolve, 10));
              }
            }

            const ORIGINAL = Symbol("original");
            const MODIFIED = Symbol("modified");

            const proxyEventHandler = {
              get(target, prop) {
                if (prop === "isTrusted") {
                  return true;
                }
                const result = Reflect.get(target, prop);
                return typeof result === "function" ? result.bind(target) : result;
              }
            };

            function preprocessEvent(e) {
              if ((e.target instanceof Element && e.target.matches('input[type="checkbox"]'))) {
                return new Proxy(e, proxyEventHandler);
              }
              return e;
            }

            Object.assign(Element.prototype, {
              attachShadow: new Proxy(Element.prototype.attachShadow, {
                apply(target, thisArg, args) {
                  thisArg._shadowRoot = target.apply(thisArg, args);
                  return thisArg._shadowRoot;
                }
              }),
              addEventListener: new Proxy(Element.prototype.addEventListener, {
                apply(target, thisArg, args) {
                  const [type, listener, options] = args;
                  if (listener instanceof Object) {
                    if (!listener[MODIFIED]) {
                      const newListener = typeof listener === "function" ? function (e) {
                        return listener.call(this, preprocessEvent(e));
                      } : function (e) {
                        return listener.handleEvent(preprocessEvent(e));
                      };
                      listener[MODIFIED] = newListener;
                      newListener[ORIGINAL] = listener;
                    }
                    args[1] = listener[MODIFIED];
                  }
                  return Reflect.apply(target, thisArg, args);
                }
              }),
              removeEventListener: new Proxy(Element.prototype.removeEventListener, {
                apply(target, thisArg, args) {
                  const [type, listener, options] = args;
                  if (listener instanceof Object) {
                    args[1] = listener[ORIGINAL] ?? listener;
                  }
                  return Reflect.apply(target, thisArg, args);
                }
              })
            });

            for (const [property, value] of Object.entries({
              visibilityState: "visible",
              webkitVisibilityState: "visible",
              hidden: false,
              webkitFalse: false
            })) {
              try {
                Object.defineProperty(document, property, { get: () => value });
              } catch (e) {
                console.error(`Cannot define document.${property}`, e);
              }
            }

            setInterval(() => {
              const checkbox = document.body?._shadowRoot?.querySelector('input[type="checkbox"]');
              if (checkbox) {
                simulateMouseClick(checkbox);
              }
            }, 100);
        """.trimIndent()

        /**
         * This script patches stack traces to hide injected code.
         *
         * This is needed since Cloudflare checks the stack trace.
         */
        private val ERROR_PATCHER_SCRIPT = $$"""
            function errorPatcher(lines) {
              const regex = RegExp(String.raw`^(.*)\b${RegExp.escape(location.href)}:(\d+):(\d+)$`);

              function patch(error) {
                error.stack = error.stack.split('\n').reduce((acc, line) => {
                  const match = line.match(regex);
                  if (match) {
                    const row = parseInt(match[2]);
                    if (row > lines) {
                      acc += `\n${match[1]}${location.href}:${row - lines}:${match[3]}`
                    }
                  } else {
                    acc += '\n';
                    acc += line;
                  }
                  return acc;
                }, "").substring(1);
                return error;
              }

              const proxyErrorHandler = {
                apply(target, thisArg, args) {
                  return patch(Reflect.apply(target, thisArg, args));
                },
                construct(target, args) {
                  return patch(Reflect.construct(target, args));
                }
              };

              for (const prop of Object.getOwnPropertyNames(window)) {
                try {
                  if (window[prop] === Error || window[prop]?.prototype instanceof Error) {
                    Object.defineProperty(window, prop, {value: new Proxy(window[prop], proxyErrorHandler)});
                  }
                } catch {}
              }
            }
        """.trimIndent()

        private val BASE_LINE_COUNT = ERROR_PATCHER_SCRIPT.count { it == '\n' } + 1
    }
}
