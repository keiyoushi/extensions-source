package eu.kanade.tachiyomi.extension.en.dcuniverseinfinite

import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val record_count: Int = 0,
    val records: Map<String, List<RecordDto>> = emptyMap(),
) {
    val items: List<RecordDto>
        get() = records.values.firstOrNull().orEmpty()
}

@Serializable
class RecordDto(
    val uuid: String,
    val title: String? = null,
    val slug: String? = null,
    val description: String? = null,
    val base_asset_url: String? = null,
    val series_uuid: String? = null,
    val series_title: String? = null,
    val series_slug: String? = null,
    val issue_number: String? = null,
    val first_released: String? = null,
    val pages: Int? = null,
)

@Serializable
class SeriesDto(
    val uuid: String,
    val title: String,
    val slug: String? = null,
    val description: String? = null,
    val age_rating: String? = null,
    val base_asset_url: String? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
class TagDto(
    val name: String,
    val categories: List<String> = emptyList(),
)

@Serializable
class RightsDto(
    val rights: RightsInfoDto = RightsInfoDto(),
    val user_guid: String? = null,
)

@Serializable
class RightsInfoDto(
    val can_read: Boolean = false,
)

@Serializable
class DownloadDto(
    val page: Int = 1,
    val num_pages: Int = 1,
    val images: List<DownloadImageDto> = emptyList(),
)

@Serializable
class DownloadImageDto(
    val page_number: Int,
    val thumbnail_url: String? = null,
    val signed_url: String? = null,
)
