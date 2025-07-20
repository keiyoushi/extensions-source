package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import kotlinx.serialization.Serializable

@Serializable
class PopularDTO(
    val result: Result,
)

@Serializable
class Result(
    val p: Int? = null,
    val next: Boolean? = null,
    val data: ArrayList<Data>,

)

@Serializable
class Data(
    val name: String,
    val photo: String,
    val nameEn: String,

)
