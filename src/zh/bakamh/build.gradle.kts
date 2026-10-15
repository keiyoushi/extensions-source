import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baka Manhua"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        name = "巴卡漫画"
        lang = "zh"
        baseUrl {
            custom("https://bakamh.com")
        }
    }
}
