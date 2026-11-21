import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LGBToon"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://lgbtoon.com"
    }

    deeplink {
        host("lgbtoon.com")
        host("www.lgbtoon.com")
        path("/title/..*")
    }
}
