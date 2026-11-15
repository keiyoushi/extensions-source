import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OtakuAngel"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://otakuangel.com"
    }

    deeplink {
        host("otakuangel.com")
        host("www.otakuangel.com")
        path("/manga/..*")
        path("/kesfet")
        path("/populer")
    }
}
