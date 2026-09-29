import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Vinne Veritas - CCC"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("en", "es").forEach {
        source {
            name = "Vinnie Veritas - CCC"
            lang = it
            baseUrl = "https://ccc.vinnieveritas.com"
        }
    }
}
