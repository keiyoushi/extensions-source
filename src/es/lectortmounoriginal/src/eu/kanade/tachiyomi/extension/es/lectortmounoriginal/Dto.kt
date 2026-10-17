package eu.kanade.tachiyomi.extension.es.lectortmounoriginal

import kotlinx.serialization.Serializable

@Serializable
class ArchiveFilterDto(val data: ArchiveFilterDataDto)

@Serializable
class ArchiveFilterDataDto(val html: String)
