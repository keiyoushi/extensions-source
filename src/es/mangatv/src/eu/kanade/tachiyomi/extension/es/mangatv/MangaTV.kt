package eu.kanade.tachiyomi.extension.es.mangatv

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class MangaTV : MangaThemesia() {
    override val mangaUrlDirectory = "/lista"
    override val datePattern = "yyyy-MM-dd"

    override val seriesDescriptionSelector = "b:contains(Sinopsis) + span"
    override fun chapterListSelector() = "#chapterlist ul.clstyle li:has(.dt a)"

    override fun pageListParse(document: Document): List<Page> {
        val unpackedScript = document.selectFirst("script:containsData(eval)")!!.data()
            .let(Unpacker::unpack)

        READER_BLOB_REGEX.find(unpackedScript)?.destructured?.toList()?.get(0)
            ?.parseAs<ReaderBlob>()
            ?.let { return decryptReaderBlob(it).mapIndexed { i, url -> Page(i, imageUrl = url) } }

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(unpackedScript)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = runCatching {
            imageListJson.replace(TRAILING_COMMA_REGEX, "]").parseAs<List<String>>()
        }.getOrElse { emptyList() }
        return imageList.mapIndexed { i, url ->
            val decodedLink = String(Base64.decode(url, Base64.DEFAULT))
            Page(i, imageUrl = "https:$decodedLink")
        }
    }

    private fun decryptReaderBlob(blob: ReaderBlob): List<String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(Base64.decode(blob.k, Base64.DEFAULT), "AES"),
                GCMParameterSpec(128, Base64.decode(blob.iv, Base64.DEFAULT)),
            )
        }
        val plaintext = cipher.doFinal(Base64.decode(blob.c, Base64.DEFAULT)).decodeToString()
        return plaintext.parseAs<ReaderImages>().p
    }

    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(mangaUrlDirectory.substring(1))
        .addQueryParameter("s", query)
        .addQueryParameter("page", page.toString())

    companion object {
        val TRAILING_COMMA_REGEX = """,\s+]""".toRegex()
        val READER_BLOB_REGEX = """ReaderBlob\.start\((\{.+?\})\)""".toRegex()
    }
}

@Serializable
class ReaderBlob {
    val k: String = ""
    val iv: String = ""
    val c: String = ""
}

@Serializable
class ReaderImages {
    val p: List<String> = emptyList()
}
