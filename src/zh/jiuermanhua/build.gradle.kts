import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "92Manhua"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "sinmh"

    source {
        name = "92漫画"
        lang = "zh"
        baseUrl = "http://www.92mh.com"
    }
}
