plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "92Manhua"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "sinmh"

    source {
        lang = "zh"
        baseUrl = "http://www.92mh.com"
    }
}
