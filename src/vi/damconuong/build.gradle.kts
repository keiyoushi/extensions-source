import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DamCoNuong"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://damconuong.name")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
