import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CuuTruyen (unoriginal)"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://cuutruyen.moe"
    }

    deeplink {
        path("/truyen/..*")
    }
}
