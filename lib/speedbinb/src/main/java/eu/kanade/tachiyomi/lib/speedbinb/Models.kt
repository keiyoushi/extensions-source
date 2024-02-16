package eu.kanade.tachiyomi.lib.speedbinb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

private val COORD_REGEX = Regex("""^i:(\d+),(\d+)\+(\d+),(\d+)>(\d+),(\d+)$""")

@Serializable
class BibContentInfo(
    val result: Int,
    val items: List<BibContentItem>,
)

@Serializable
class BibContentItem(
    @SerialName("ContentID") val contentId: String,
    @SerialName("ContentsServer") val contentServer: String,
    @SerialName("ServerType") val serverType: Int,
    val stbl: String,
    val ttbl: String,
    val ptbl: String,
    val ctbl: String,
    @SerialName("p") val requestToken: String? = null,
    @SerialName("ViewMode") val viewMode: Int,
    @SerialName("ContentDate") val contentDate: String? = null,
    @SerialName("ShopURL") val shopUrl: String? = null,
) {
    fun getSbcUrl(readerUrl: HttpUrl, cid: String) =
        contentServer.toHttpUrl().newBuilder().apply {
            when (serverType) {
                ServerType.DIRECT -> addPathSegment("content.js")
                ServerType.REST -> addPathSegment("content")
                ServerType.SBC -> {
                    addPathSegment("sbcGetCntnt.php")
                    setQueryParameter("cid", cid)
                    requestToken?.let { setQueryParameter("p", it) }
                    setQueryParameter("q", "1")
                    setQueryParameter("vm", viewMode.toString())
                    setQueryParameter("dmytime", contentDate ?: System.currentTimeMillis().toString())
                    copyKeyParametersFrom(readerUrl)
                }
                else -> throw UnsupportedOperationException("Unsupported ServerType value $serverType")
            }
        }.toString()
}

object ServerType {
    const val SBC = 0
    const val DIRECT = 1
    const val REST = 2
}

object ViewMode {
    const val COMMERCIAL = 1
    const val NON_MEMBER_TRIAL = 2
    const val MEMBER_TRIAL = 3
}

@Serializable
class PtImg(
    @SerialName("ptimg-version") val ptImgVersion: Int,
    val resources: PtImgResources,
    val views: List<PtImgViews>,
) {
    val translations by lazy {
        views[0].coords.map { coord ->
            val v = COORD_REGEX.matchEntire(coord)!!.groupValues.drop(1).map { it.toInt() }
            PtImgTranslation(v[0], v[1], v[2], v[3], v[4], v[5])
        }
    }
}

@Serializable
class PtImgResources(
    val i: PtImgImage,
)

@Serializable
class PtImgImage(
    val src: String,
    val width: Int,
    val height: Int,
)

@Serializable
class PtImgViews(
    val width: Int,
    val height: Int,
    val coords: Array<String>,
)

class PtImgTranslation(val xsrc: Int, val ysrc: Int, val width: Int, val height: Int, val xdest: Int, val ydest: Int)

@Serializable
class SBCContent(
    @SerialName("SBCVersion") val sbcVersion: String,
    val result: Int,
    val ttx: String,
    @SerialName("ImageClass") val imageClass: String? = null,
)
