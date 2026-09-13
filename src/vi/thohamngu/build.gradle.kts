import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Thỏ Ham Ngủ"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://thohamngu.xyz")
        }
    }

    deeplink {
        path("/.*")
    }
}
