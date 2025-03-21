package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable

@Serializable // Used to process login
data class AuthenticationDto(
    val username: String,
    val token: String,
    val apiKey: String,
)

@Serializable
data class PaginationInfo(
    val currentPage: Int,
    val itemsPerPage: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@Serializable
data class ServerInfoDto(
    val installId: String,
    val os: String,
    val isDocker: Boolean,
    val dotnetVersion: String,
    val kavitaVersion: String,
    val numOfCores: Int,
)
