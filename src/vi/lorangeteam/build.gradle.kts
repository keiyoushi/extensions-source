import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "L'orange Team"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://lorangeteam.art")
        }
    }

    deeplink {
        host("lorangeteam.art")
        host("www.lorangeteam.art")
        path("/truyen")
        path("/truyen/..*")
        path("/the-loai/..*")
    }
}
