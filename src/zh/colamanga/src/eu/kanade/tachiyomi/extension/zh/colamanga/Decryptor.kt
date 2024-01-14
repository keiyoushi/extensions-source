package eu.kanade.tachiyomi.extension.zh.colamanga

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.api.get
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Decryptor(preferences: SharedPreferences, client: OkHttpClient) {
    private val preferences = preferences
    private val client = client

    private var _keys: Keys? = null

    private var keys: Keys
        get() {
            if (_keys != null) {
                return _keys!!
            } else {
                try {
                    Log.i("Decryptor", "Get Keys from: ${preferences.keysUrl}")
                    val keysJson =
                        client.newCall(
                            GET(
                                preferences.keysUrl,
                                cache = CacheControl.FORCE_NETWORK,
                            ),
                        )
                            .execute()
                            .body
                            .string()
                    _keys = Json.decodeFromString<Keys>(keysJson)
                    return _keys!!
                } catch (e: Exception) {
                    Log.e("Decryptor", "Get Keys Error: $e")
                    throw e
                }
            }
        }
        set(value) {
            _keys = value
        }

    private fun decrypt(data: ByteArray, key: String): String? {
        Log.i("Decryptor", "using key: $key")
        try {
            val aesKey = SecretKeySpec(key.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey)
            val result = cipher.doFinal(data)
            val stringResult = result.toString(Charsets.UTF_8)
            Log.i("Decryptor", "Decrypted Data: $stringResult")
            return stringResult
        } catch (e: Exception) {
            Log.e("decrypt", "Decrypt Data Error: $e")
        }
        return null
    }

    private fun jsd(keys: List<String>, data: String, twice: Boolean): String {
        Log.i("Decryptor", "keys: $keys, data: $data, twice: $twice")
        var decodedData = Base64.decode(data, Base64.DEFAULT)
        if (twice) {
            decodedData = Base64.decode(decodedData, Base64.DEFAULT)
        }

        for (key in keys) {
            val result = decrypt(decodedData, key)
            if (result != null) {
                return result
            }
        }
        return ""
    }

    fun decryptCData(cData: String): String {
        return jsd(keys.C_DATA, cData, true)
    }

    fun decryptPageNumber(pageNumber: String): String {
        return jsd(keys.enc_code1, pageNumber, true)
    }

    fun decryptPageUrl(pageUrl: String): String {
        return jsd(keys.enc_code2, pageUrl, true)
    }

    fun decryptImgKey(imgKey: String): String {
        return jsd(keys.C_DATA, imgKey, false)
    }

    fun getImgKey(keyType: String, imgKey: String): String {
        if (keyType.isEmpty() || keyType == "0") {
            return decryptImgKey(imgKey)
        } else {
            Log.i("getImgKey", "keyType: $keyType")
            return keys.img_key.get(keyType) ?: ""
        }
    }

    @Serializable
    class Keys(
        val C_DATA: List<String>,
        val enc_code1: List<String>,
        val enc_code2: List<String>,
        val img_key: Map<String, String>,
    )
}
