import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Newtoki (SBXH)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl {
            custom("https://sbxh9.com")
        }
    }

    deeplink {
        path("/webtoon/..*")
        path("/manhwa/..*")
    }
}
