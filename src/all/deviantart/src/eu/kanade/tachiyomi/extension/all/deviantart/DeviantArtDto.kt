package eu.kanade.tachiyomi.extension.all.deviantart

import keiyoushi.utils.getObjectOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class Deviation(
    // val deviationId: Int,
    // val type: String, // "image", "film"
    val url: String, // "https://www.deviantart.com/shw67/art/Beach-58-1007661685"
    val title: String,
    // val isVideo: Boolean,
    // val publishedTime: String, // "2024-01-03T03:17:25-0800"
    // val isMature: Boolean,
    val author: Author,
    val media: DeviationMedia,
)

@Serializable
class DeviationState(
    // val deviationId: Int,
    // val type: String, // "image", "film"
    val url: String, // "https://www.deviantart.com/shw67/art/Beach-58-1007661685"
    val title: String,
    // val isVideo: Boolean,
    // val publishedTime: String, // "2024-01-03T03:17:25-0800"
    // val isMature: Boolean,
    val author: Int,
    val media: DeviationMedia,
)

@Serializable
class Author(
    // val userId: Int,
    // val useridUuid: String, // "8460dab0-bcab-49e8-bbae-18dc5c677b55"
    val username: String, // "shw67",
    // val usericon: String, // "https://a.deviantart.net/avatars-big/s/h/shw67.jpg?2"
)

@Serializable
class DeviationMedia(
    private val baseUri: String?, // "https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/8460dab0-bcab-49e8-bbae-18dc5c677b55/dgnxoqd-054db8b2-5baf-41f3-809f-39b94925415b.jpg"
    private val prettyName: String?, // "beach__58__by_shw67_dgnxoqd"
    private val token: List<String>? = null,
    // "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7ImhlaWdodCI6Ijw9MTgwMCIsInBhdGgiOiJcL2ZcLzg0NjBkYWIwLWJjYWItNDllOC1iYmFlLTE4ZGM1YzY3N2I1NVwvZGdueG9xZC0wNTRkYjhiMi01YmFmLTQxZjMtODA5Zi0zOWI5NDkyNTQxNWIuanBnIiwid2lkdGgiOiI8PTEyMDAifV1dLCJhdWQiOlsidXJuOnNlcnZpY2U6aW1hZ2Uub3BlcmF0aW9ucyJdfQ.aF5SPOVJhAKAMgJxReo14ywf5upUjQg4_e56npVlLeo",
    // "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7InBhdGgiOiJcL2ZcLzg0NjBkYWIwLWJjYWItNDllOC1iYmFlLTE4ZGM1YzY3N2I1NVwvZGdueG9xZC0wNTRkYjhiMi01YmFmLTQxZjMtODA5Zi0zOWI5NDkyNTQxNWIuanBnIn1dXSwiYXVkIjpbInVybjpzZXJ2aWNlOmZpbGUuZG93bmxvYWQiXX0.V6UcjpOb2UYTe0inj8ffUkLNWR6zlwRyZ1_4VKyp4GQ"
    private val types: List<DeviationMediaType>?,
) {
    fun getCoverUrl(): String? {
        val uri = baseUri ?: return null
        // A token only authorizes the type that points at it: a resized path or the original file
        val jwt = coverType?.let { token?.getOrNull(it.r) } ?: return uri
        return "$uri${getPreviewPath()}?token=$jwt"
    }

    // No `c` means the preview is the original file ($uri?token=$jwt instead of $uri/v1/fill/...-pre.jpg?token=$jwt)
    // Return: /v1/fill/w_...-pre.jpg
    private fun getPreviewPath(): String = coverType
        ?.c?.replace("<prettyName>", prettyName.orEmpty()).orEmpty()

    private fun getThumbnailPath() = (thumbnailType ?: smallestType)
        ?.c?.replace("<prettyName>", prettyName.orEmpty()).orEmpty()

    private val previewType get() = types?.firstOrNull { it.t == "preview" }
    private val thumbnailType get() = types?.firstOrNull { it.t == "400T" } // 400px height
    private val coverType get() = thumbnailType ?: previewType ?: smallestType
    private val smallestType get() = types?.filter { it.w > 0 && it.h > 0 }?.minByOrNull { it.w * it.h }
}

@Serializable
class DeviationMediaType(
    val t: String, // fullview, social_preview, preview, 400T, video
    val c: String? = null, // path segments: /v1/fill/w_1600,h_2400,q_80,strp/<prettyName>-fullview.jpg
    val r: Int = 0, // index into `token`
    val h: Int = 0, // height
    val w: Int = 0, // width
)

@Serializable
class User(
    // val user: Author,
    val deviations: List<Deviation>,
)

@Serializable
class Collection(
    // val collection: CollectionInfo,
    val deviations: List<Deviation>,
)

// @Serializable
// class CollectionInfo(
//     val folderId: Int,
//     val name: String,
//     val owner: Author,
// )

@Serializable
class StateContent(
    @SerialName("@@entities")
    val entities: StateEntities,
    // Streams keyed by name, each with its own shape, and some names include an ID, so read them by key below
    @SerialName("@@streams")
    private val streams: JsonObject? = null,
) {
    // Only on gallery pages. Lazy so it's parsed once, and only when read: a delegated property isn't serialized,
    // while a plain initialized one would be parsed while decoding every page's state, art pages included
    val galleryFolderIds: FolderIdStream? by lazy {
        streams?.getObjectOrNull("GALLERY_FOLDER_IDS_STREAM")?.parseAs<FolderIdStream>()
    }

    // On a folder's gallery page, e.g. `folder-deviations-gallery--2` on /{username}/gallery/scraps
    fun folderDeviationIds(folderId: Long): FolderDeviationStream? = streams?.getObjectOrNull("folder-deviations-gallery-$folderId")
        ?.parseAs<FolderDeviationStream>()
}

@Serializable
class StateEntities(
    val user: JsonObject,
    val deviation: JsonObject,
    val galleryFolder: Map<String, GalleryFolder> = emptyMap(),
)

// The user's folders in display order, "All" first
@Serializable
class FolderIdStream(
    val items: List<Long>, // folder IDs
    // True when the user has more folders than the page lists (25 plus "All"); the site loads the rest as you scroll
    val hasMore: Boolean = false,
)

// The deviations of one folder; see the TODO in fetchGalleryChapterList
@Serializable
class FolderDeviationStream(
    val items: List<Long>, // deviation IDs
    // True when the folder has more deviations than the page lists (only the first ones); the site loads
    // the rest, 24 at a time from `nextOffset`, through its gallery API as you scroll
    val hasMore: Boolean = false,
)

@Serializable
class GalleryFolder(
    val folderId: Long,
    val name: String,
    val specialType: String? = null, // "all", "scraps", "featured"
    val thumb: Long? = null, // deviation ID of the cover
)

@Serializable
class RCacheContent(
    val relatedContent: RelatedContents?,
)

@Serializable
class RelatedContents(
    val relatedContent: List<JsonElement>?,
)

@Serializable
class Related(
    val contentType: String?,
    val deviations: List<Deviation>?,
    val users: List<User>?,
    val collections: List<Collection>?,
)
