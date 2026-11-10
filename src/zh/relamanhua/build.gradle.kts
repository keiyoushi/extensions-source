import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Relamanhua"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "熱辣漫畫"
        lang = "zh"
        baseUrl = "https://m.2024manga.com"
    }

    deeplink {
        host("m.2024manga.com")
        host("www.2024manga.com")
        host("2024manga.com")
        host("m.manga2026.xyz")
        host("www.manga2026.xyz")
        host("manga2026.xyz")
        path("/comic/..*")
        path("/v2h5/details/comic/..*")
        path("/v2h5/comicContent/..*")
    }
}
