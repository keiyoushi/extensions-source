import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeTruyen18"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://metruyen18.sbs")
        }
        versionId = 2
    }

    deeplink {
        host("metruyen18.sbs")
        host("www.metruyen18.sbs")
        path("/truyen/..*")
        path("/kham-pha")
    }
}
