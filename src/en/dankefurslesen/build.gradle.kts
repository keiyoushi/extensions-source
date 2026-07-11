import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Danke fürs Lesen"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "guya"

    source {
        lang = "en"
        baseUrl = "https://danke.moe"
    }
}
