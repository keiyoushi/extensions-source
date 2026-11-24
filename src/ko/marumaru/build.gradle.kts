import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Marumaru"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "마루마루"
        lang = "ko"
        baseUrl {
            custom("https://marumaru112.com")
        }
    }

    deeplink {
        path("/webtoon/..*")
        path("/manhwa/..*")
        path("/work/..*")
    }
}
