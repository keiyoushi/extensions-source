import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HOLONOMETRIA"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("ja", "en", "id").forEach {
        source {
            lang = it
            baseUrl = "https://holoearth.com"
        }
    }
}
