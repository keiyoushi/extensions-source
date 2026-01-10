package eu.kanade.tachiyomi.lib.clipstudioreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

abstract class ClipStudioReader : HttpSource() {
    override val client = super.client.newBuilder()
        .addInterceptor(Deobfuscator())
        .addInterceptor(ImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun pageListParse(response: Response): List<Page> {
        val requestUrl = response.request.url
        val contentId = requestUrl.queryParameter("c")

        if (contentId != null) {
            // EPUB-based path
            val tokenUrl = "$baseUrl/api/tokens/viewer?content_id=$contentId".toHttpUrl()
            val tokenResponse = client.newCall(GET(tokenUrl, headers)).execute()
            val viewerToken = tokenResponse.parseAs<TokenResponse>().token

            val metaUrl = "$baseUrl/api/contents/$contentId/meta".toHttpUrl()
            val apiHeaders = headersBuilder().add("Authorization", "Bearer $viewerToken").build()
            val metaResponse = client.newCall(GET(metaUrl, apiHeaders)).execute()
            val contentBaseUrl = metaResponse.parseAs<MetaResponse>().content.baseUrl

            val preprocessUrl = "$contentBaseUrl/preprocess-settings.json"
            val obfuscationResponse = client.newCall(GET(preprocessUrl, headers)).execute()
            val obfuscationKey = obfuscationResponse.parseAs<PreprocessSettings>().obfuscateImageKey

            val containerUrl = "$contentBaseUrl/META-INF/container.xml"
            val containerResponse = client.newCall(GET(containerUrl, headers)).execute()
            val containerDoc = Jsoup.parse(containerResponse.body.string(), containerUrl, Parser.xmlParser())
            val opfPath = containerDoc.selectFirst("*|rootfile")?.attr("full-path")
                ?: throw Exception("Failed to find rootfile in container.xml")

            val opfUrl = (contentBaseUrl.removeSuffix("/") + "/" + opfPath).toHttpUrl()
            val opfResponse = client.newCall(GET(opfUrl, headers)).execute()
            val opfDoc = opfResponse.asJsoup()

            val imageManifestItems = opfDoc.select("*|item[media-type^=image/]")
                .sortedBy { it.attr("href") }
            if (imageManifestItems.isEmpty()) {
                throw Exception("No image pages found in the EPUB manifest")
            }

            return imageManifestItems.mapIndexed { i, item ->
                val href = item.attr("href")
                    ?: throw Exception("Image item found with no href")
                val imageUrlBuilder = opfUrl.resolve(href)!!.newBuilder()
                obfuscationKey.let {
                    imageUrlBuilder.addQueryParameter("obfuscateKey", it.toString())
                }
                Page(i, imageUrl = imageUrlBuilder.build().toString())
            }
        }

        // param/cgi-based XML path
        // param/cgi in URL
        var authkey = requestUrl.queryParameter("param")?.replace(" ", "+")
        var endpoint = requestUrl.queryParameter("cgi")

        // param/cgi in HTML
        if (authkey.isNullOrEmpty() || endpoint.isNullOrEmpty()) {
            val document = response.asJsoup()
            authkey = document.selectFirst("div#meta input[name=param]")?.attr("value")
                ?: throw Exception("Could not find auth key")
            endpoint = document.selectFirst("div#meta input[name=cgi]")?.attr("value")
                ?: throw Exception("Could not find endpoint")
        }

        val viewerUrl = baseUrl.toHttpUrl().resolve(endpoint)
            ?: throw Exception("Could not resolve endpoint URL: $endpoint")

        val faceUrl = viewerUrl.newBuilder().apply {
            addQueryParameter("mode", MODE_DL_FACE_XML)
            addQueryParameter("reqtype", REQUEST_TYPE_FILE)
            addQueryParameter("vm", "4")
            addQueryParameter("file", "face.xml")
            addQueryParameter("param", authkey)
        }.build()

        val faceResponse = client.newCall(GET(faceUrl, headers)).execute()
        if (!faceResponse.isSuccessful) throw Exception("HTTP error ${faceResponse.code} while fetching face.xml")
        val faceData = faceResponse.use { parseFaceData(it.asJsoup()) }

        return (0 until faceData.totalPages).map { i ->
            val pageFileName = i.toString().padStart(4, '0') + ".xml"
            val pageXmlUrl = viewerUrl.newBuilder().apply {
                addQueryParameter("mode", MODE_DL_PAGE_XML)
                addQueryParameter("reqtype", REQUEST_TYPE_FILE)
                addQueryParameter("vm", "4")
                addQueryParameter("file", pageFileName)
                addQueryParameter("param", authkey)
                // Custom params
                addQueryParameter("csr_sw", faceData.scrambleWidth.toString())
                addQueryParameter("csr_sh", faceData.scrambleHeight.toString())
            }.build()
            Page(i, url = pageXmlUrl.toString())
        }
    }

    override fun imageUrlParse(response: Response): String {
        val requestUrl = response.request.url
        val document = response.asJsoup()

        val authkey = requestUrl.queryParameter("param")!!
        val scrambleGridW = requestUrl.queryParameter("csr_sw")!!
        val scrambleGridH = requestUrl.queryParameter("csr_sh")!!
        // Reconstruct endpoint without query params
        val endpointUrl = requestUrl.newBuilder().query(null).build()

        val pageIndex = document.selectFirst("PageNo")?.text()?.toIntOrNull()
            ?: throw Exception("Could not find PageNo")
        val scrambleArray = document.selectFirst("Scramble")?.text()
        val parts = document.select("Kind").mapNotNull {
            val type = it.text().toIntOrNull()
            val number = it.attr("No")
            val isScrambled = it.attr("scramble") == "1"
            if (type == null || number.isEmpty()) return@mapNotNull null

            val partFileName = "${pageIndex.toString().padStart(4, '0')}_${number.padStart(4, '0')}.bin"
            PagePart(partFileName, type, isScrambled)
        }

        val imagePart = parts.firstOrNull { it.type in SUPPORTED_IMAGE_TYPES }
            ?: throw Exception("No supported image parts found for page")

        val imageUrlBuilder = endpointUrl.newBuilder().apply {
            addQueryParameter("mode", imagePart.type.toString())
            addQueryParameter("file", imagePart.fileName)
            addQueryParameter("reqtype", REQUEST_TYPE_FILE)
            addQueryParameter("param", authkey)
        }

        if (imagePart.isScrambled && !scrambleArray.isNullOrEmpty()) {
            imageUrlBuilder.apply {
                addQueryParameter("scrambleArray", scrambleArray)
                addQueryParameter("scrambleGridW", scrambleGridW)
                addQueryParameter("scrambleGridH", scrambleGridH)
            }
        }
        return imageUrlBuilder.build().toString()
    }

    private fun parseFaceData(document: Document): FaceData {
        val totalPages = document.selectFirst("TotalPage")?.text()?.toIntOrNull()
        val scrambleWidth = document.selectFirst("Scramble > Width")?.text()?.toIntOrNull()
        val scrambleHeight = document.selectFirst("Scramble > Height")?.text()?.toIntOrNull()
        return FaceData(totalPages!!, scrambleWidth!!, scrambleHeight!!)
    }

    companion object {
        private const val MODE_DL_FACE_XML = "7"
        private const val MODE_DL_PAGE_XML = "8"
        private const val REQUEST_TYPE_FILE = "0"

        private val SUPPORTED_IMAGE_TYPES = setOf(1, 2, 3) // JPEG, GIF, PNG
    }
}
