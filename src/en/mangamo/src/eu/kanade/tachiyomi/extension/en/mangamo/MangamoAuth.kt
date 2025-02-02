package eu.kanade.tachiyomi.extension.en.mangamo

import eu.kanade.tachiyomi.extension.en.mangamo.MangamoHelper.Companion.parseJson
import eu.kanade.tachiyomi.extension.en.mangamo.dto.FirebaseAuthDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.FirebaseRegisterDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.MangamoLoginDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.TokenRefreshDto
import eu.kanade.tachiyomi.network.POST
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_HEADERS

class MangamoAuth(
    private val helper: MangamoHelper,
    private val client: OkHttpClient,
    private val userToken: String,
) {

    private lateinit var currentToken: String
    private lateinit var refreshToken: String
    private var expirationTime: Long = 0

    fun getIdToken(): String {
        synchronized(this) {
            if (!this::currentToken.isInitialized) {
                obtainInitialIdToken()
            }
            refreshIfNecessary()
            return currentToken
        }
    }

    fun forceRefresh() {
        obtainInitialIdToken()
    }

    private fun expireIn(seconds: Long) {
        expirationTime = System.currentTimeMillis() + (seconds - 1) * 1000
    }

    private fun obtainInitialIdToken() {
        val mangamoLoginResponse = client.newCall(
            POST(
                "${MangamoConstants.FIREBASE_FUNCTION_BASE_PATH}/v3/login",
                helper.jsonHeaders,
                "{\"purchaserInfo\":{\"originalAppUserId\":\"$userToken\"}}".toRequestBody(),
            ),
        ).execute()

        val customToken = mangamoLoginResponse.body.string().parseJson<MangamoLoginDto>().accessToken

        val googleIdentityResponse = client.newCall(
            POST(
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${MangamoConstants.FIREBASE_API_KEY}",
                EMPTY_HEADERS,
                "{\"token\":\"$customToken\",\"returnSecureToken\":true}".toRequestBody(),
            ),
        ).execute()

        val tokenInfo = googleIdentityResponse.body.string().parseJson<FirebaseAuthDto>()

        currentToken = tokenInfo.idToken
        refreshToken = tokenInfo.refreshToken
        expireIn(tokenInfo.expiresIn)
    }

    private fun refreshIfNecessary() {
        if (System.currentTimeMillis() > expirationTime) {
            val headers = Headers.Builder()
                .add("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val refreshResponse = client.newCall(
                POST(
                    "https://securetoken.googleapis.com/v1/token?key=${MangamoConstants.FIREBASE_API_KEY}",
                    headers,
                    "grant_type=refresh_token&refresh_token=$refreshToken".toRequestBody(),
                ),
            ).execute()

            if (refreshResponse.code == 200) {
                val tokenInfo = refreshResponse.body.string().parseJson<TokenRefreshDto>()

                currentToken = tokenInfo.idToken
                refreshToken = tokenInfo.refreshToken
                expireIn(tokenInfo.expiresIn)
            } else {
                // Refresh token may have expired
                obtainInitialIdToken()
            }
        }
    }

    companion object {
        fun createAnonymousUserToken(client: OkHttpClient): String {
            val googleIdentityResponse = client.newCall(
                POST(
                    "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${MangamoConstants.FIREBASE_API_KEY}",
                    EMPTY_HEADERS,
                    "{\"returnSecureToken\":true}".toRequestBody(),
                ),
            ).execute()

            val tokenInfo = googleIdentityResponse.body.string().parseJson<FirebaseRegisterDto>()

            return tokenInfo.localId
        }
    }
}
