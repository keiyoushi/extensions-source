import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenTuoiTho"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl = "https://truyentuoitho.com"
    }
}
