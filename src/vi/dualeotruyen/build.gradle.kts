import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dua Leo Truyen"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Dưa Leo Truyện"
        lang = "vi"
        baseUrl {
            custom("https://dualeotruyenbet.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
