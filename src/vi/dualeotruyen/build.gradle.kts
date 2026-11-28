import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dua Leo Truyen"
    versionCode = 41
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Dưa Leo Truyện"
        lang = "vi"
        baseUrl {
            custom("https://dualeotruyentj.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
