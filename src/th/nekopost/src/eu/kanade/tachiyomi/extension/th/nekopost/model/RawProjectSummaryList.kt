package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class RawProjectSummaryList(
    val code: Int,
    val listChapter: List<RawProjectSummary>?,
)
