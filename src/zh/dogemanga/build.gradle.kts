import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doge Manga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "漫畫狗"
        lang = "zh"
        baseUrl = "https://dogemanga.com"
    }

    deeplink {
        path("/m/..*")
    }
}
