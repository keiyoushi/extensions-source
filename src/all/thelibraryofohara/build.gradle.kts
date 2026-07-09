plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Library of Ohara"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("id", "en", "es", "it", "ar", "fr").forEach {
        source {
            lang = it
            baseUrl = "https://thelibraryofohara.com"
        }
    }
}
