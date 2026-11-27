import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DDToon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "뚜툰"
        lang = "ko"
        baseUrl {
            custom("https://ddtoon33.com")
        }
    }

    deeplink {
        path("/mview1.php")
        path("/mview2.php")
    }
}
