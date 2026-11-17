import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HangTruyenOnline"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://hangtruyen.online"
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
