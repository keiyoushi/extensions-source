import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "18Manhua"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "goda"

    source {
        name = "18漫画"
        lang = "zh"
        baseUrl = "https://18mh.org"
    }
}
