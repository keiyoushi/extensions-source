import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VietManhwa"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://vietmanhwa.org"
        lang = "vi"
    }

    deeplink {
        path("/manhwa-18/..*")
        path("/the-loai/..*")
        path("/author/..*")
        path("/authors/..*")
        path("/translators/..*")
        path("/characters/..*")
        path("/doujinshi/..*")
    }
}
