package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.internal.BaseUrl
import org.gradle.api.provider.Property

abstract class ExtensionSource {
    abstract val name: Property<String>
    abstract val lang: Property<String>
    internal abstract val resolvedBaseUrl: Property<BaseUrl>
    abstract val versionId: Property<Int>
    abstract val id: Property<Long>

    var baseUrl: String
        get() = error("baseUrl is write-only")
        set(value) {
            resolvedBaseUrl.set(BaseUrl.Static(value))
        }

    fun baseUrl(block: BaseUrlConfig.() -> Unit) {
        resolvedBaseUrl.set(BaseUrlConfig().apply(block).build())
    }
}
