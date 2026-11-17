import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ung Ty Comics"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Ưng Tỷ Comics"
        lang = "vi"
        baseUrl = "https://ungtycomics.net"
    }

    deeplink {
        host("ungtycomics.net")
        host("www.ungtycomics.net")
        path("/truyen/..*")
        path("/tim-truyen.*")
        path("/truyen-hot")
        path("/truyen-tranh")
    }
}
