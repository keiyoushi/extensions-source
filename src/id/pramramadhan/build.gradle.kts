import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pramramadhan"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://01.pramramadhan.my.id"
    }

    deeplink {
        host("01.pramramadhan.my.id")
        host("pramramadhan.my.id")
        path("/series/..*")
    }
}
