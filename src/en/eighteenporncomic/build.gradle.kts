import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "18 Porn Comic"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "manga18"

    source {
        lang = "en"
        baseUrl = "https://18porncomic.com"
    }
}
