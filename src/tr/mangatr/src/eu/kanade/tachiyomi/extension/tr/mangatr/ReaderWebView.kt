package eu.kanade.tachiyomi.extension.tr.mangatr

import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.serialization.Serializable
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Opens a chapter in a WebView and lets the site's own reader show it, then reads the page urls it displays, in page
 * order. The reader keeps changing how it hides those urls; whatever it does, it has to put the images on screen.
 */
internal suspend fun readPagesInWebView(chapterUrl: String, userAgent: String, origin: String): List<String> {
    val result = try {
        runWebView<String>(timeout = 60.seconds) {
            this.userAgent = userAgent
            injectAtDocumentStart(CANVAS_HOOK, setOf(origin))
            onReceivedError { request, error ->
                if (request.isForMainFrame) reject(IOException(error.description.toString()))
            }
            poll(500.milliseconds) {
                evaluateJs(COLLECT_PAGES) { output ->
                    output.parseAs<String?>()?.let(::resolve)
                }
            }
            loadUrl(chapterUrl)
        }
    } catch (e: WebViewTimeoutException) {
        throw IOException("Bölüm sayfası WebView'da yüklenemedi", e)
    }
    val pages = result.parseAs<WebViewPages>()
    if (pages.login) throw IOException(LOGIN_REQUIRED)
    return pages.pages
}

@Serializable
private class WebViewPages(val pages: List<String> = emptyList(), val login: Boolean = false)

internal const val LOGIN_REQUIRED = "Bu bölümü okuyabilmek için WebView üzerinden üye girişi yapmanız gerekmektedir."

// Remembers which image each canvas was drawn from, as a drawn canvas keeps no url of its own
private const val CANVAS_HOOK = """
(() => {
    const draw = CanvasRenderingContext2D.prototype.drawImage;
    CanvasRenderingContext2D.prototype.drawImage = function (image, ...rest) {
        if (image instanceof HTMLImageElement && !this.canvas.dataset.kSrc) this.canvas.dataset.kSrc = image.currentSrc || image.src;
        return draw.call(this, image, ...rest);
    };
})();
"""

// Zooms the page out so the lazy reader loads every page, then returns the page urls once their count stops growing
private const val COLLECT_PAGES = """
(() => {
    if (!document.body || document.readyState !== 'complete') return null;
    if (/Giriş Yapmalısınız/.test(document.body.innerText)) return JSON.stringify({ login: true });
    const state = window.__kPages = window.__kPages || { start: Date.now(), changed: Date.now(), count: -1 };
    document.documentElement.style.zoom = '0.02';
    window.scrollTo(0, document.documentElement.scrollHeight);

    // A chapter can repeat a page, so every image counts, in the order the reader shows them
    const urls = [];
    const reader = document.querySelector('.chapter-content') || document.body;
    for (const el of reader.querySelectorAll('img, canvas, [style*="background-image"]')) {
        let url = null;
        if (el.tagName === 'IMG') url = el.currentSrc || el.src;
        else if (el.tagName === 'CANVAS') url = el.dataset.kSrc;
        else url = (/url\(["']?([^"')]+)/.exec(el.style.backgroundImage) || [])[1];
        if (!url || !/^https:\/\/image\.mngtr\.site\//.test(url) || /\/kapak\//.test(url)) continue;
        urls.push(url);
    }
    if (urls.length !== state.count) {
        state.count = urls.length;
        state.changed = Date.now();
    }
    const slots = document.querySelectorAll('.reader-page-wrap, .reader-webtoon-wrap').length;
    const settled = Date.now() - state.changed > (urls.length >= slots ? 2500 : 8000);
    if ((urls.length > 0 && settled) || Date.now() - state.start > 45000) return JSON.stringify({ pages: urls });
    return null;
})()
"""
