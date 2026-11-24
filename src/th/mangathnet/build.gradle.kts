import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-TH.Net"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://manga-th.net"
    }

    deeplink {
        host("manga-th.net")
        host("www.manga-th.net")
        path("/manga/..*")
        path("/manhwa/..*")
        path("/manhua/..*")
        path("/doujin/..*")
        path("/chapter/..*")
        path("/library")
        path("/library/..*")
    }
}
