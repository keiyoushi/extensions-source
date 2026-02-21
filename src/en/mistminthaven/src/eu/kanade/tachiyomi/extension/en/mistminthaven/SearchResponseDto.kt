package eu.kanade.tachiyomi.extension.en.mistminthaven

data class SearchResponseDto(
    val paging: SearchResponsePageDto,
    val data: List<SearchResponseDataDto>,
)

data class SearchResponsePageDto(
    val currentPage: Int,
    val totalItems: Int,
    val totalPages: Int,
)
data class SearchResponseDataDto(
    val id: String,
    val title: String,
    val slug: String,
    val description: String,
    val author: String,
    val illustrator: String?,
    val avatarUrl: String,
    val nativeLanguage: String,
    val isMature: Boolean,
    val status: Int,
    val views: Int,
    val createdAt: String,
    val createdBy: CreatedBy,
    val genres: List<Genre>,
)

data class CreatedBy(
    val id: String,
    val username: String,
)

data class Genre(
    val id: String,
    val name: String,
)
