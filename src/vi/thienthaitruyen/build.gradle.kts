import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ThienThaiTruyen"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://thienthaitruyen12.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
