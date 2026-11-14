import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cookmana"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl {
            custom("https://cookmana12.com")
        }
        lang = "ko"
    }

    deeplink {
        host("cookmana11.com")
        host("cookmana12.com")
        host("cookmana13.com")
        path("/episode/..*")
        path("/detail/..*")
    }
}
