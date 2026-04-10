package eu.kanade.tachiyomi.extension.vi.cmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class CMangaAlbumListResponse {
    var status: Int = 0
    var message: String? = null
    var data: CMangaAlbumListData? = null
}

@Serializable
class CMangaAlbumListData {
    var data: List<CMangaAlbumItem> = emptyList()
    var total: Int = 0
}

@Serializable
class CMangaAlbumItem {
    @SerialName("id_album")
    var idAlbum: Long? = null
    var info: String? = null
    var data: String? = null

    @SerialName("last_update")
    var lastUpdate: String? = null
    var file: String? = null
}

@Serializable
class CMangaAlbumByIdResponse {
    var status: Int = 0
    var message: String? = null
    var data: CMangaAlbumByIdData? = null
}

@Serializable
class CMangaAlbumByIdData {
    var info: String? = null
    var data: String? = null
    var file: String? = null
}

@Serializable
class CMangaAlbumInfo {
    var url: String? = null
    var name: String? = null
    var tags: List<String> = emptyList()
    var avatar: String? = null
    var detail: String? = null
    var status: String? = null
    var author: List<String> = emptyList()
}

@Serializable
class CMangaChapterListResponse {
    var status: Int = 0
    var message: String? = null
    var data: List<CMangaChapterItem>? = null
}

@Serializable
class CMangaChapterItem {
    @SerialName("id_chapter")
    var idChapter: Long? = null
    var info: String? = null
}

@Serializable
class CMangaChapterInfo {
    var id: JsonElement? = null
    var num: JsonElement? = null
    var name: String? = null

    @SerialName("last_update")
    var lastUpdate: String? = null
    var level: JsonElement? = null
    var lock: CMangaChapterLock? = null
}

@Serializable
class CMangaChapterLock {
    var end: JsonElement? = null
    var level: JsonElement? = null
    var fee: JsonElement? = null
}

@Serializable
class CMangaChapterImageResponse {
    var status: Int = 0
    var message: String? = null
    var data: CMangaChapterImageData? = null
}

@Serializable
class CMangaChapterImageData {
    var status: Int = 0
    var image: List<String>? = null
    var level: Int? = null
}

@Serializable
class CMangaUserSecurity {
    var id: JsonElement? = null
    var token: JsonElement? = null
}
