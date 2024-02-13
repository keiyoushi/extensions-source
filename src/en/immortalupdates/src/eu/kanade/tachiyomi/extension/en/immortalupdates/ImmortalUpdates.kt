package eu.kanade.tachiyomi.extension.en.immortalupdates

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ImmortalUpdates : Madara("Immortal Updates", "https://immortalupdates.com", "en") {

    override val useNewChapterEndpoint: Boolean = true

    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())

            if (response.request.url.fragment?.contains(DESCRAMBLE) != true) {
                return@addInterceptor response
            }
            val fragment = response.request.url.fragment!!
            val args = fragment.substringAfter("$DESCRAMBLE=").split(",")

            val image = unscrambleImage(response.body.byteStream(), args)
            val body = image.toResponseBody("image/jpeg".toMediaTypeOrNull())
            return@addInterceptor response.newBuilder()
                .body(body)
                .build()
        }.build()

    override fun pageListParse(document: Document): List<Page> {
        val pageList = super.pageListParse(document).toMutableList()

        val unscramblingCallsPage = pageList.firstOrNull { it.imageUrl!!.contains("00-call") }
            ?: return pageList

        val unscramblingCalls = client.newCall(GET(unscramblingCallsPage.imageUrl!!, headers))
            .execute()
            .use { it.body.string() }

        unscramblingCalls.replace("\r", "").split("\n").filter { !it.isNullOrBlank() }.forEach {
            val args = unfuckJs(it)
                .substringAfter("[")
                .substringBefore("]")

            val filenameFragment = args.split(",")[0].removeSurrounding("\"")
            val page = pageList.firstOrNull { page -> page.imageUrl!!.contains(filenameFragment, ignoreCase = true) }
                ?: return@forEach
            val newPageUrl = page.imageUrl!!.toHttpUrl().newBuilder()
                .fragment("$DESCRAMBLE=$args")
                .build()
                .toString()
            pageList[page.index] = Page(page.index, document.location(), newPageUrl)
        }
        pageList.remove(unscramblingCallsPage)
        return pageList
    }

    // Converted from _0x3bc005: Find the CanvasRenderingContext2D.drawImage call basically
    //
    // `args` is the arguments of the original get_img call:
    //     get_img(file_to_match, indexer, iterations, sectionWidth, sectionHeight, isBackgroundBlack, shouldFillColor, ???, key, keyAddition, ???)
    //
    // The boolean after shouldFillColor seems to always be `false` so I have optimized it out for now.
    // If it fucks up someone will make an issue anyways /shrug
    //
    // I assumed the last argument was to check if versions match or something (since it was 1.0.1)
    // but it was used in some canvas thingy that I didn't bother to check
    private fun unscrambleImage(image: InputStream, args: List<String>): ByteArray {
        val indexer = args[1].toInt()
        val iterations = args[2].toInt()
        val sectionWidth = args[3].toInt()
        val sectionHeight = args[4].toInt()
        val isBackgroundBlack = args[5] == "true"
        val shouldFillColor = args[6] == "true"
        val key = args[8].toInt()
        val keyAddition = args[9].toInt()

        val bitmap = BitmapFactory.decodeStream(image)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val heightSectionCount = bitmap.height / sectionHeight
        val widthSectionCount = bitmap.width / sectionWidth
        val sectionCount = heightSectionCount * widthSectionCount
        val descramblingArray = createDescramblingArray(indexer, sectionCount, key, keyAddition, iterations)

        if (shouldFillColor) {
            val backgroundColor = if (isBackgroundBlack) Color.BLACK else Color.WHITE
            canvas.drawColor(backgroundColor)
        }

        var i = 0
        for (vertical in 0 until heightSectionCount) {
            for (horizontal in 0 until widthSectionCount) {
                val swap = descramblingArray[i]

                val baseHeight = swap.floorDiv(widthSectionCount)
                val baseWidth = swap - baseHeight * widthSectionCount

                val dx = baseWidth * sectionWidth
                val dy = baseHeight * sectionHeight

                val sx = horizontal * sectionWidth
                val sy = vertical * sectionHeight

                val srcRect = Rect(sx, sy, sx + sectionWidth, sy + sectionHeight)
                val dstRect = Rect(dx, dy, dx + sectionWidth, dy + sectionHeight)

                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                i += 1
            }
        }

        if (isBackgroundBlack) {
            val invertingPaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                            0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                            0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                            0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
                        ),
                    ),
                )
            }
            canvas.drawBitmap(result, 0f, 0f, invertingPaint)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)

        return output.toByteArray()
    }

    // Converted from _0x144afb
    // This should be called a little bit before the drawImage calls
    private fun createDescramblingArray(indexer: Int, size: Int, key: Int, keyAddition: Int, iterations: Int = 2): List<Int> {
        var indexerMut = indexer
        val returnArray = mutableListOf<Int>()

        for (i in 0 until size) {
            returnArray.add(i)
        }

        for (i in 0 until size) {
            for (o in 0 until iterations) {
                indexerMut = (indexerMut * key + keyAddition) % size

                val tmp = returnArray[indexerMut]
                returnArray[indexerMut] = returnArray[i]
                returnArray[i] = tmp
            }
        }

        return returnArray
    }

    private fun unfuckJs(jsf: String): String {
        // String: ([]+[])
        // fontcolor: (![]+[])[+[]]+({}+[])[+!![]]+([][[]]+[])[+!![]]+(!![]+[])[+[]]+({}+[])[!![]+!![]+!![]+!![]+!![]]+({}+[])[+!![]]+(![]+[])[!![]+!![]]+({}+[])[+!![]]+(!![]+[])[+!![]]
        // "undefined": []+[][[]]
        // Quick hack so QuickJS doesn't complain about function being called with no args
        val input = jsf.replace(
            "([]+[])[(![]+[])[+[]]+({}+[])[+!![]]+([][[]]+[])[+!![]]+(!![]+[])[+[]]+({}+[])[!![]+!![]+!![]+!![]+!![]]+({}+[])[+!![]]+(![]+[])[!![]+!![]]+({}+[])[+!![]]+(!![]+[])[+!![]]]()",
            "([]+[])[(![]+[])[+[]]+({}+[])[+!![]]+([][[]]+[])[+!![]]+(!![]+[])[+[]]+({}+[])[!![]+!![]+!![]+!![]+!![]]+({}+[])[+!![]]+(![]+[])[!![]+!![]]+({}+[])[+!![]]+(!![]+[])[+!![]]]([]+[][[]])",
        )
        return QuickJs.create().use {
            it.execute(jsfBoilerplate)
            it.evaluate("get_img_data(${input.removePrefix("[]").removeSuffix("()")}[0])").toString()
        }
    }

    private val jsfBoilerplate: ByteArray by lazy {
        QuickJs.create().use {
            it.compile(
                """
                var _0x56dfa1=_0x217c;(function(_0x458c1d,_0x5a2370){var _0x4d7856=_0x217c,_0x1fa20f=_0x458c1d();while(!![]){try{var _0x34da05=-parseInt(_0x4d7856(0xac))/0x1+-parseInt(_0x4d7856(0xbc))/0x2*(-parseInt(_0x4d7856(0xb3))/0x3)+-parseInt(_0x4d7856(0xb8))/0x4+-parseInt(_0x4d7856(0xbb))/0x5*(parseInt(_0x4d7856(0xbd))/0x6)+parseInt(_0x4d7856(0xba))/0x7*(-parseInt(_0x4d7856(0xae))/0x8)+-parseInt(_0x4d7856(0xb0))/0x9+-parseInt(_0x4d7856(0xb9))/0xa*(-parseInt(_0x4d7856(0xaf))/0xb);if(_0x34da05===_0x5a2370)break;else _0x1fa20f['push'](_0x1fa20f['shift']());}catch(_0x7dd169){_0x1fa20f['push'](_0x1fa20f['shift']());}}}(_0x3c3e,0x4ade6));class Location{constructor(_0x2d256a){var _0x3e4741=_0x217c;this[_0x3e4741(0xaa)]=_0x2d256a;}[_0x56dfa1(0xb7)](){var _0x6040fb=_0x56dfa1;return this[_0x6040fb(0xaa)];}}function _0x3c3e(){var _0x27ebfd=['mtu0nJCYmLfWu2Hpvq','C3rYAw5NAwz5','z2v0x2LTz19KyxrH','mJfyB3f3sxq','Bg9JyxrPB24','Ahr0Chm6lY8','sLnptG','Dg9tDhjPBMC','nta3mZq0rhbJv1jR','mtyYotaWnZb4rxnwAeO','mtK2nJa5qMHUBMTW','mJqYndaWnwjTsu5nAq','ndGWmtjpAgv4sKu','nKrSy1Pcza','AhjLzG','zxzHBa','mte3mdy1ywTgEuLY','z2v0x2LTzW','mty4vLP5D05q','mtfIBgDhyue'];_0x3c3e=function(){return _0x27ebfd;};return _0x3c3e();}function _0x217c(_0x3aa1f3,_0x1793f5){var _0x3c3edb=_0x3c3e();return _0x217c=function(_0x217ce0,_0x3d5a46){_0x217ce0=_0x217ce0-0xaa;var _0x14b4f0=_0x3c3edb[_0x217ce0];if(_0x217c['DsHVWi']===undefined){var _0x59277=function(_0x2d256a){var _0x54ceb8='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/=';var _0x41845c='',_0x15e7f4='';for(var _0x269688=0x0,_0x3cc079,_0x25d416,_0xa002fc=0x0;_0x25d416=_0x2d256a['charAt'](_0xa002fc++);~_0x25d416&&(_0x3cc079=_0x269688%0x4?_0x3cc079*0x40+_0x25d416:_0x25d416,_0x269688++%0x4)?_0x41845c+=String['fromCharCode'](0xff&_0x3cc079>>(-0x2*_0x269688&0x6)):0x0){_0x25d416=_0x54ceb8['indexOf'](_0x25d416);}for(var _0x1d6105=0x0,_0x225416=_0x41845c['length'];_0x1d6105<_0x225416;_0x1d6105++){_0x15e7f4+='%'+('00'+_0x41845c['charCodeAt'](_0x1d6105)['toString'](0x10))['slice'](-0x2);}return decodeURIComponent(_0x15e7f4);};_0x217c['fyYiDX']=_0x59277,_0x3aa1f3=arguments,_0x217c['DsHVWi']=!![];}var _0x19df2c=_0x3c3edb[0x0],_0x478efb=_0x217ce0+_0x19df2c,_0x5309ed=_0x3aa1f3[_0x478efb];return!_0x5309ed?(_0x14b4f0=_0x217c['fyYiDX'](_0x14b4f0),_0x3aa1f3[_0x478efb]=_0x14b4f0):_0x14b4f0=_0x5309ed,_0x14b4f0;},_0x217c(_0x3aa1f3,_0x1793f5);}this[_0x56dfa1(0xb4)]=new Location(_0x56dfa1(0xb5)),this[_0x56dfa1(0xad)]=(..._0x54ceb8)=>[..._0x54ceb8],this[_0x56dfa1(0xb2)]=_0x41845c=>this[_0x56dfa1(0xb6)][_0x56dfa1(0xb1)](this[_0x56dfa1(0xab)](_0x41845c));
                """.trimIndent(),
                "?",
            )
        }
    }

    companion object {
        const val DESCRAMBLE = "descramble"
    }
}
