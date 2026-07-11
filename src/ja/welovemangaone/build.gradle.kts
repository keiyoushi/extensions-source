plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Love4u"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://love4u.net"
        id = 1647179844716143786L
    }
}
