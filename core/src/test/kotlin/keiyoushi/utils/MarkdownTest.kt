package keiyoushi.utils

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTest {
    @Test
    fun `plain text is unchanged`() {
        listOf(
            "Re: Zero - Kara", "AT&T", "&amp;", "2.5 Dimensional", "a | b", "a > b", "Hero #1",
            "#hashtag", "-x", "+x", "a`b", "(a)!",
        ).forEach {
            assertEquals(it, it.escapeMarkdown())
        }
    }

    @Test
    fun `line starts are escaped`() {
        assertEquals("""\# Title""", "# Title".escapeMarkdown())
        assertEquals("""\> quote""", "> quote".escapeMarkdown())
        assertEquals("""\- item""", "- item".escapeMarkdown())
        assertEquals("""\+ item""", "+ item".escapeMarkdown())
        assertEquals("""\---""", "---".escapeMarkdown())
        assertEquals("""\~~~ title""", "~~~ title".escapeMarkdown())
        assertEquals("""\===""", "===".escapeMarkdown())
        assertEquals("""\:--|--""", ":--|--".escapeMarkdown())
        assertEquals("""\|---|---|""", "|---|---|".escapeMarkdown())
        assertEquals("""1\. item""", "1. item".escapeMarkdown())
        assertEquals("""2020\)""", "2020)".escapeMarkdown())
    }

    @Test
    fun `every line is escaped`() {
        assertEquals("Title\n\\---\n\n\\# Next", "Title\n---\n\n# Next".escapeMarkdown())
    }

    @Test
    fun `inline markers are escaped`() {
        assertEquals("""\*a\* \_b\_ \`c`""", "*a* _b_ `c`".escapeMarkdown())
        assertEquals("""!\[a\](http://b)""", "![a](http://b)".escapeMarkdown())
        assertEquals("""\<b>""", "<b>".escapeMarkdown())
        assertEquals("""a \\~ b""", """a \~ b""".escapeMarkdown())
    }

    @Test
    fun `indentation is removed`() {
        assertEquals("code", "    code".escapeMarkdown())
    }

    private fun html(html: String) = Jsoup.parseBodyFragment(html, "https://site.test/series/").body().toMarkdown()

    @Test
    fun `paragraphs and line breaks`() {
        assertEquals("One\ntwo\n\nThree", html("<p>One<br>two</p>\n<p>Three</p>"))
        assertEquals("One\n\nTwo", html("<div>One</div><div>Two</div>"))
    }

    @Test
    fun `text is escaped but keeps its spaces`() {
        assertEquals("""a \*b\* **c** d""", html("<p>a *b* <b>c</b> d</p>"))
        assertEquals("\\# Title", html("<p># Title</p>"))
    }

    @Test
    fun `emphasis keeps spaces outside its markers`() {
        assertEquals("a **b** c", html("a<b> b </b>c"))
        assertEquals("*a*", html("<em>a</em>"))
        assertEquals("**a b**", html("<strong>a <b>b</b></strong>"))
        assertEquals("", html("<b> </b>"))
    }

    @Test
    fun `headings become bold`() {
        assertEquals("**Title**\n\nText", html("<h2>Title</h2><p>Text</p>"))
    }

    @Test
    fun `links are absolute`() {
        assertEquals("[a](https://site.test/x%281%29)", html("""<a href="/x(1)">a</a>"""))
        assertEquals("a", html("<a>a</a>"))
        assertEquals("", html("""<a href="/x"><img src="/y"></a>"""))
    }

    @Test
    fun `lists`() {
        assertEquals("- a\n- b", html("<ul><li>a</li><li>b</li></ul>"))
        assertEquals("1. a\n2. b", html("<ol><li>a</li><li>b</li></ol>"))
        assertEquals("- a\n  - b", html("<ul><li>a<ul><li>b</li></ul></li></ul>"))
    }

    @Test
    fun `images and scripts are dropped`() {
        assertEquals("a", html("""<p>a<img src="/x"></p><script>b()</script><style>c{}</style>"""))
    }

    @Test
    fun `ratingStars matches half stars`() {
        assertEquals("★★★★★", ratingStars(10.0))
        assertEquals("★★★★★", ratingStars(9.6))
        assertEquals("★★★★✬", ratingStars(9.5))
        assertEquals("★★★★☆", ratingStars(8.0))
        assertEquals("✬☆☆☆☆", ratingStars(1.0))
        assertEquals("☆☆☆☆☆", ratingStars(0.0))
        assertEquals("★★★✬☆", ratingStars(3.5, max = 5))
    }

    @Test
    fun `newlines in text are kept on request`() {
        val html = "One\ntwo <b>three\nfour</b>\n<a\nhref=\"/x\">five</a>"
        val body = Jsoup.parseBodyFragment(html, "https://site.test/").body()
        assertEquals("One two **three four** [five](https://site.test/x)", body.toMarkdown())
        assertEquals("One\ntwo three\nfour\n[five](https://site.test/x)", body.toMarkdown(keepNewlines = true))
    }

    @Test
    fun `description sections in order`() {
        val description = MarkdownDescription.Builder()
            .rating(8.25, votes = 12)
            .info("2019", null, " ", "Manhwa")
            .description("# Intro\nSecond line")
            .field("Publisher", "A_B")
            .field("Year", null)
            .field("Parodies", listOf("x", " ", "y"))
            .altTitles(listOf("One", " ", "One", "*Two*"))
            .links(listOf("AniList" to "https://anilist.co/manga/1", "MAL" to null, "My (List)" to "https://x.test/a b", "AniList" to "https://anilist.co/manga/2"))
            .build()
        assertEquals(
            """
            ★★★★☆ 8.25 (12) · 2019 · Manhwa

            \# Intro
            Second line

            **Publisher:** A\_B · **Parodies:** x, y

            **Alternative Names:**
            - One
            - \*Two\*

            **Links:**
            - [AniList](https://anilist.co/manga/1)
            - [My (List)](https://x.test/a%20b)
            - [AniList](https://anilist.co/manga/2)
            """.trimIndent(),
            description,
        )
    }

    @Test
    fun `description builder skips empty input`() {
        assertNull(
            MarkdownDescription.Builder()
                .rating(null)
                .info(null, "")
                .description(null as String?)
                .description(Jsoup.parseBodyFragment("<img src=a>").body())
                .markdown(" ")
                .field("A", " ")
                .altTitles(emptyList())
                .links(listOf("C" to ""))
                .rating(Double.NaN)
                .build(),
        )
    }

    @Test
    fun `description builder takes html and markdown`() {
        val description = MarkdownDescription.Builder()
            .field("A", "1")
            .description(Jsoup.parseBodyFragment("<p>a <b>b</b></p>").body())
            .field("B", "2")
            .markdown("**kept**")
            .rating(85, max = 100)
            .build()
        assertEquals("**A:** 1\n\na **b**\n\n**B:** 2\n\n**kept**\n\n★★★★☆ 85", description)
    }
}
