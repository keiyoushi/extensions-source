import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "11toon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl = "https://www.11toon.com"
    }

    deeplink {
        host("www.11toon.com")
        host("11toon.com")
        path("/bbs/board.php")
    }
}
