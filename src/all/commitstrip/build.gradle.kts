import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Commit Strip"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("en", "fr").forEach {
        source {
            lang = it
            baseUrl = "https://www.commitstrip.com"
        }
    }
}
