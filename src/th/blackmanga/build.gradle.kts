import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Black Manga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://www.black-manga.com"
    }

    deeplink {
        host("www.black-manga.com")
        host("black-manga.com")
        path("/..*")
    }
}
