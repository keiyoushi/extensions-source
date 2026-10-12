import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ThienThaiTruyen"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://thienthaitruyen16.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
