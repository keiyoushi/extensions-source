import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DamCoNuong"
    versionCode = 17
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://damconuong.win")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}
