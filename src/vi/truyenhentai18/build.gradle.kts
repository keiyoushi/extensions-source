import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Truyen Hentai 18+"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Truyện Hentai 18+"
        lang = "vi"
        baseUrl {
            custom("https://truyenhentai18.net")
        }
    }

    deeplink {
        path("/..*\\.html")
    }
}
