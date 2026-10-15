import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tr Manga"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    deeplink {
        path("/webtoon/..*")
    }

    source {
        name = "TrManga"
        lang = "tr"
        baseUrl = "https://trmanga.com"
    }
}
