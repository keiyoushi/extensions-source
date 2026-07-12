import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manta Comics"
    versionCode = 9
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            name = "Manta"
            lang = it
            baseUrl = "https://manta.net/$it"
        }
    }
}
