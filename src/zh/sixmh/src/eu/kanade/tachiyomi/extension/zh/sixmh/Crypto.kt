package eu.kanade.tachiyomi.extension.zh.sixmh

import android.util.Base64
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt

internal fun decodeData(encodedData: String): String {
    // Key derived from https://www.liumanhua.com/template/pc/liumanhua/js/index-v2.js line 571
    // excerpt:
    //   var _0x493e85 = CryptoJS[_0x5b6369(0x15c,'a#uL')]['Utf8'][_0x5b6369(0x147,'H0qZ')](_0x5b6369(0x152,')X69'));
    //   _0x5b6369(0x152,')X69') provides the key
    val aesKey = "9S8\$vJnU2ANeSRoF".encodeToByteArray()

    val decodedBase64 = Base64.decode(encodedData, Base64.DEFAULT)
    val iv = decodedBase64.sliceArray(0 until 16)
    val cipherText = decodedBase64.sliceArray(16 until decodedBase64.size)

    return cipherText.aesDecrypt(aesCbc(aesKey, iv)).decodeToString()
}
