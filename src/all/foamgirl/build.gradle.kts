plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FoamGirl"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://foamgirl.net"
    }
}
