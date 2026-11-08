import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Relamanhua"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "熱辣漫畫"
        lang = "zh"
        baseUrl = "https://m.2024manga.com"
    }

    deeplink {
        path("/v2h5/details/comic/..*")
        path("/v2h5/comicContent/..*")
    }
}
