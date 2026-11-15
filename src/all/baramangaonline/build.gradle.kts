import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bara Manga Online"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://baramangaonline.com"
    }

    deeplink {
        host("baramangaonline.com")
        host("www.baramangaonline.com")
        path("/..*")
    }
}
