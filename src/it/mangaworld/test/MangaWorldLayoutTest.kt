package eu.kanade.tachiyomi.extension.it.mangaworld

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaWorldLayoutTest {
    @Test
    fun chapterAndReaderUseContentLinksInDocumentOrder() {
        val base = "https://www.mangaworld.me/"
        val detail = Jsoup.parse(
            """<a href="/read/first">Primo capitolo</a><section id="capitoli"><a class="ch" href="/read/1199"><span class="ch-label">Capitolo 1199</span></a></section>""",
            base,
        )
        val chapter = detail.chapterLinks().single()
        assertEquals("Capitolo 1199", chapter.text())
        assertEquals("${base}read/1199", chapter.absUrl("href"))

        val reader = Jsoup.parse("""<img src="/logo.svg"><img data-reader-image src="/1.jpg"><img data-reader-image src="/2.jpg">""", base)
        assertEquals(listOf("${base}1.jpg", "${base}2.jpg"), reader.pageImages())
    }
}
