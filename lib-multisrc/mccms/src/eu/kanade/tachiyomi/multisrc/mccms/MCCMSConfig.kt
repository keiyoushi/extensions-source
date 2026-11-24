package eu.kanade.tachiyomi.multisrc.mccms

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.select.Evaluator

const val PAGE_SIZE = 30

val pcHeaders = Headers.headersOf("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0")

fun String.removePathPrefix() = removePrefix("/index.php")

fun String.mobileUrl() = replace("//www.", "//m.")

open class MCCMSConfig(
    val hasCategoryPage: Boolean = true,
    val textSearchOnlyPageOne: Boolean = false,
    val useMobilePageList: Boolean = false,
    private val lazyLoadImageAttr: String = "data-original",
    private val encryptedPageList: Boolean = false,
) {
    fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return when {
            encryptedPageList -> {
                val params = document.select("script").firstNotNullOf { PARAMS_REGEX.find(it.data()) }.groupValues[1]
                decryptParams(params).images.mapIndexed { i, image ->
                    Page(i, imageUrl = image)
                }
            }
            useMobilePageList -> {
                val container = document.selectFirst(Evaluator.Class("comic-list"))!!
                container.select(Evaluator.Tag("img")).mapIndexed { i, img ->
                    Page(i, imageUrl = img.attr("src"))
                }
            }
            else -> {
                document.select("img[$lazyLoadImageAttr]").mapIndexed { i, img ->
                    Page(i, imageUrl = img.attr(lazyLoadImageAttr))
                }
            }
        }
    }
}

private val PARAMS_REGEX = Regex("""params = '([^']+)'""")

// Key from the site reader's script
private val PARAMS_KEY = "9S8\$vJnU2ANeSRoF".toByteArray()

private fun decryptParams(literal: String): ImageListDto {
    // A JavaScript string literal, with \/ and \u002b escapes on some sites
    val params = "\"$literal\"".parseAs<String>()
    val data = Base64.decode(params, Base64.DEFAULT)
    val cipher = aesCbc(PARAMS_KEY, data.copyOfRange(0, 16))
    return data.copyOfRange(16, data.size).aesDecrypt(cipher).decodeToString().parseAs()
}
