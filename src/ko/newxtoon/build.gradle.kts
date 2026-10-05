import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NewXToon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl {
            custom("https://newxtoon1.com")
        }
    }

    deeplink {
        path("/comics/..*")
    }
}
