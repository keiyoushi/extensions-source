import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dua Leo Truyen"
    versionCode = 24
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Dưa Leo Truyện"
        lang = "vi"
        baseUrl {
            custom("https://dualeotruyenhn.com")
        }
    }
}
