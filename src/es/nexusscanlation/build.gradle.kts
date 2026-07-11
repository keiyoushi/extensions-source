plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NexusScanlation"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://nexusscanlation.com"
    }
}
