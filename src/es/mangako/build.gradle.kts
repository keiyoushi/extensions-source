import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaKo"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://mangako.xyz"
    }

    deeplink {
        host("mangako.xyz")
        host("www.mangako.xyz")
        path("/title/..*")
        path("/chapter/..*")
        path("/scan/..*")
        path("/searcher/..*")
    }
}
