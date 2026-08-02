import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VCPVMP"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
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
