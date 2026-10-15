import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Library of Ohara"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("id", "en", "es", "it", "ar", "fr").forEach {
        source {
            lang = it
            baseUrl = "https://thelibraryofohara.com"
        }
    }
}
