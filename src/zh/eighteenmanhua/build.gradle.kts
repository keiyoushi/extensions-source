import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "18Manhua"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "goda"

    source {
        name = "18漫画"
        lang = "zh"
        baseUrl = "https://18mh.org"
    }
}
