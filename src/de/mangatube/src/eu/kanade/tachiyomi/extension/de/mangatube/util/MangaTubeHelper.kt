package eu.kanade.tachiyomi.extension.de.mangatube.util

import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response

class MangaTubeHelper {

    companion object {
        fun mangaStatus(status: Int): Int {
            return when (status) {
                0 -> SManga.ONGOING
                1 -> SManga.ON_HIATUS
                2 -> SManga.LICENSED
                3 -> SManga.CANCELLED
                4 -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }

        fun checkResponse(response: Response): String {
            val body = response.body.string()

            if (response.code != 200) {
                throw Exception("Unexpected network issue")
            }

            if (body.startsWith("<!DOCTYPE html>")) {
                throw Exception("IP isn't verified. Open webview!")
            }

            return body
        }
    }
}
