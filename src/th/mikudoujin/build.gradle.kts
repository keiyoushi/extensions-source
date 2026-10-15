import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikuDoujin"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl {
            custom("https://miku-doujin.com")
        }
    }

    deeplink {
        path("/..*")
    }
}
