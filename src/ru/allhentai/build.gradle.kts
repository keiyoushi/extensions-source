import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllHentai"
    versionCode = 25
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://20.allhen.online")
        }
        lang = "ru"
        id = 1809051393403180443L
    }
}
