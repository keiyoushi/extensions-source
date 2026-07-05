plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Commit Strip"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "fr").forEach {
        source {
            lang = it
            baseUrl = "https://www.commitstrip.com"
        }
    }
}
