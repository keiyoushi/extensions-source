import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayHentai"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl {
            custom("https://sayhentai.cx")
        }
    }
}
