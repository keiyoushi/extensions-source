import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenQQ VN"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://truyenqq.com.vn"
        lang = "vi"
    }

    deeplink {
        path("/..*/chapter-..*")
        path("/..*")
    }
}
