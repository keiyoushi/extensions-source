import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NixManga"
    versionCode = 2
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://nixmanga.com"
    }
}
