import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dua Leo Truyen"
    versionCode = 36
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Dưa Leo Truyện"
        lang = "vi"
        baseUrl {
            custom("https://dualeotruyenjv.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
