plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manta Comics"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            name = "Manta"
            lang = it
            baseUrl = "https://manta.net/$it"
        }
    }
}
