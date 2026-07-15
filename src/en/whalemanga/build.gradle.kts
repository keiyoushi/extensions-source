import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Whale Manga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "WhaleManga"
        lang = "en"
        baseUrl = "https://whalemanga.com"
    }
}
