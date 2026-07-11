plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "92Manhua"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "sinmh"

    source {
        name = "92漫画"
        lang = "zh"
        baseUrl = "http://www.92mh.com"
    }
}
