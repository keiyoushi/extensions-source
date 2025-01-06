package eu.kanade.tachiyomi.extension.ru.hotmanga.dto

import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val id: Long,
    val lastChapterId: Long?,
    val lastChapterBranchId: Long?,
    val slug: String,
    val type: String,
    val title: String,
    val alternativeTitle: String?,
    val titleEn: String?,
    val desc: String?,
    val robotDesc: String?,
    val imageMid: String?,
    val imageLow: String?,
    val imageHigh: String,
    val isAccessRu: Boolean,
    val isSubscription: Boolean,
    val isYaoi: Boolean,
    val isSafe: Boolean,
    val isHomo: Boolean,
    val isHentai: Boolean,
    val isYuri: Boolean,
    val isConfirm: Boolean,
    val needUploadImage: Boolean,
    val status: String,
    val countChapters: Long,
    val source: String,
    val redirectUrl: String?,
    val languageType: String,
    val newUploadAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val createdRedirectUrlAt: String?,
)
