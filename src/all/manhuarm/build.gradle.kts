import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    versionCode = 25
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madara"

    listOf("ar", "en", "es", "fr", "id", "it", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://manhuarmtl.com"
        }
    }
}
