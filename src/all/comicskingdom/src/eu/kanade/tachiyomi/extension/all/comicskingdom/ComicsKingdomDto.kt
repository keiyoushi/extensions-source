package eu.kanade.tachiyomi.extension.all.comicskingdom

import kotlinx.serialization.Serializable

class ComicsKingdomDto {

    @Serializable
    data class Chapter(
        val link: String,
        val date: String,
        val assets: Assets?,
    )

    @Serializable
    data class Assets(
        val single: AssetData,
        val featured: AssetData,
    )

    @Serializable
    data class AssetData(
        val url: String,
        val width: Int,
        val height: Int,
        val altText: String,
    )
}
