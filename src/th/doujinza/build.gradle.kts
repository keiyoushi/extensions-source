import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinZa"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "th"
        baseUrl = "https://doujinza.com"
    }
}
