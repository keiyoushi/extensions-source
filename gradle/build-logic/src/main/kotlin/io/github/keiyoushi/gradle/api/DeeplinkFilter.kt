package io.github.keiyoushi.gradle.api

import java.io.Serializable

data class DeeplinkFilter(
    val hosts: List<String>,
    val pathPatterns: List<String>,
) : Serializable
