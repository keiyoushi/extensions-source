import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "izneo (webtoons)"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "fr").forEach {
        source {
            name = "izneo"
            lang = it
            baseUrl = "https://www.izneo.com/$it/webtoon"
            versionId = 2
        }
    }
}
