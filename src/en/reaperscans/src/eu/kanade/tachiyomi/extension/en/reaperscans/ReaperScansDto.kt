import eu.kanade.tachiyomi.multisrc.heancms.HeanCmsPageFileDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ReaperPagePayloadDto(
    val chapter: ReaperPageDto,
    private val paywall: Boolean = false,
    val data: List<String>? = emptyList(),
) {
    fun isPaywalled() = paywall
}

@Serializable
class ReaperPageDto(
    @SerialName("chapter_data") val chapterData: ReaperPageDataDto?,
)

@Serializable
class ReaperPageDataDto(
    private val images: List<String>? = emptyList(),
    private val files: List<HeanCmsPageFileDto>? = emptyList(),
) {
    fun images(): List<String> {
        return if (images.isNullOrEmpty()) {
            files?.map {
                it.url
            }.orEmpty()
        } else {
            images
        }
    }
}
