package eu.kanade.tachiyomi.extension.en.fsicomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class FSIComics : HttpSource() {
    override val name = "FSI Comics"
    override val baseUrl = "https://fsicomics.com"
    override val supportsLatest = true

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    override val client = network.client

    // Popular manga - fetch categories as manga series
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/wp-json/wp/v2/categories?per_page=100&page=$page&_fields=id,name,description,link,count,parent", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val categories = json.parseToJsonElement(response.body.string()).jsonArray
        val mangas = categories.map { category ->
            val catObj = category.jsonObject
            SManga.create().apply {
                val link = catObj["link"]!!.jsonPrimitive.content
                url = link.substringAfter(baseUrl)
                title = catObj["name"]!!.jsonPrimitive.content
                description = catObj["description"]?.jsonPrimitive?.content ?: ""
                thumbnail_url = null
                status = SManga.UNKNOWN
            }
        }.filter { it.title.isNotBlank() }
        return MangasPage(mangas, categories.size == 100)
    }

    // Latest manga - fetch recent posts and group by category
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/wp-json/wp/v2/posts?per_page=20&page=$page&_fields=id,title,link,categories,date,featured_media", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val posts = json.parseToJsonElement(response.body.string()).jsonArray
        val mangas = posts.map { post ->
            val postObj = post.jsonObject
            SManga.create().apply {
                val link = postObj["link"]!!.jsonPrimitive.content
                url = link.substringAfter(baseUrl)
                title = postObj["title"]!!.jsonObject["rendered"]!!.jsonPrimitive.content
                thumbnail_url = null
                status = SManga.UNKNOWN
            }
        }
        return MangasPage(mangas, posts.size == 20)
    }

    // Search manga
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/wp-json/wp/v2/posts?per_page=20&page=$page&search=$query&_fields=id,title,link,categories,date,featured_media", headers)

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Manga details - fetch from category page
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            description = document.selectFirst(".archive-description, .term-description, .category-description")?.text() ?: ""
            thumbnail_url = document.selectFirst(".entry-content img, .category-image img")?.attr("src")
            status = SManga.UNKNOWN
        }
    }

    // Chapter list - fetch posts from category
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        // Extract chapter links from the category page
        document.select("article a[href], .entry-title a[href], .post-title a[href]").forEach { element ->
            val href = element.attr("href")
            if (href.contains(baseUrl) && !href.contains("/category/") && !href.contains("/tag/")) {
                val title = element.text()
                if (title.isNotBlank() && !chapters.any { it.url == href.substringAfter(baseUrl) }) {
                    chapters.add(
                        SChapter.create().apply {
                            url = href.substringAfter(baseUrl)
                            name = title
                            date_upload = 0L
                        },
                    )
                }
            }
        }

        // If no chapters found from links, try to parse pagination and fetch all posts
        if (chapters.isEmpty()) {
            return fetchChaptersFromApi(response)
        }

        return chapters.reversed()
    }

    private fun fetchChaptersFromApi(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val categoryLink = document.selectFirst("link[rel='canonical']")?.attr("href") ?: return emptyList()

        // Extract category slug from URL
        val categorySlug = categoryLink.split("/").filter { it.isNotBlank() }.lastOrNull() ?: return emptyList()

        // Fetch posts from this category via API
        val apiUrl = "$baseUrl/wp-json/wp/v2/posts?per_page=100&categories=$categorySlug&_fields=id,title,link,date"
        val apiResponse = client.newCall(GET(apiUrl, headers)).execute()
        val posts = json.parseToJsonElement(apiResponse.body.string()).jsonArray

        return posts.map { post ->
            val postObj = post.jsonObject
            SChapter.create().apply {
                val link = postObj["link"]!!.jsonPrimitive.content
                url = link.substringAfter(baseUrl)
                name = postObj["title"]!!.jsonObject["rendered"]!!.jsonPrimitive.content
                date_upload = parseDate(postObj["date"]?.jsonPrimitive?.content ?: "")
            }
        }.reversed()
    }

    // Page list - extract images from post content
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return extractImagesFromDocument(document)
    }

    private fun extractImagesFromDocument(document: Document): List<Page> {
        val images = mutableListOf<Page>()
        val imageElements = document.select(".entry-content img, .post-content img, article img")

        imageElements.forEachIndexed { index, img ->
            val imageUrl = img.attr("data-orig-file").ifEmpty {
                img.attr("data-large-file").ifEmpty {
                    img.attr("src")
                }
            }

            if (imageUrl.isNotBlank() && isImageUrl(imageUrl)) {
                images.add(Page(images.size, "", imageUrl))
            }
        }

        return images
    }

    private fun isImageUrl(url: String): Boolean {
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
        return imageExtensions.any { url.lowercase().contains(it) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun parseDate(dateStr: String): Long = try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        format.parse(dateStr)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
