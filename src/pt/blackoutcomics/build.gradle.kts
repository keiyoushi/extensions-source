import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Blackout Comics"
    versionCode = 10
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://blackoutcomics.com"
    }
}
