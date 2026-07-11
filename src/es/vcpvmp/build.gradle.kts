plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VCPVMP"
    versionCode = 9
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "vercomics"

    source {
        name = "VCP"
        lang = "es"
        baseUrl = "https://vercomicsporno.com"
    }

    source {
        name = "VMP"
        lang = "es"
        baseUrl = "https://vermangasporno.com"
    }
}
