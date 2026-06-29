package eu.kanade.tachiyomi.extension.tr.alucardscans

import kotlinx.serialization.Serializable

@Serializable
data class AlucardResponse(
    val series: List<AluSeries>? = emptyList(), // Popüler ve Arama için
    val groupedChapters: List<AluGroupedChapters>? = null, // Latest Updates için
    val chapters: List<AluChapters>? = null, // Bölüm listesi için (gerekirse)
    val pagination: AluPagination? = null,
)

@Serializable
data class AluSeries(
    val id: String? = null,
    val title: String? = null,
    val cover: String? = null, // Detay sayfasından gelen
    val coverImage: String? = null, // Listelerden gelen
    val slug: String? = null,
    val type: String? = null,
    val status: String? = null,
    val description: String? = null,
    val genres: List<String>? = emptyList(),
    val author: String? = null,
    val artist: String? = null,
)

@Serializable
data class AluGroupedChapters(
    val series: AluSeries? = null, // Buradaki seri nesnesi AluSeries ile aynı yapıda
    val chapters: List<AluChapters>? = emptyList(),
)

@Serializable
data class AluChapters(
    val id: String? = null,
    val title: String? = null,
    val number: String? = null,
    val slug: String? = null,
    val releaseDate: String? = null,
    val isPremium: Boolean? = false,
)

@Serializable
data class AluPagination(
    val total: Int? = 0,
    val page: Int? = 1,
    val limit: Int? = 24,
    val pages: Int? = 1,
)
