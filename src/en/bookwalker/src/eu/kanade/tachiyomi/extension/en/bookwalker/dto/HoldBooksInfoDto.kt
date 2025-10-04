package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
class HoldBooksInfoDto(
    val holdBookList: HoldBookListDto,
)

@Serializable
class HoldBookListDto(
    val entities: List<HoldBookEntityDto>,
)

@Serializable
@JsonClassDiscriminator("type")
sealed class HoldBookEntityDto
