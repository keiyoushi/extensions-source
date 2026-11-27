import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DamCoNuong"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://damconuong.help")
        }
    }

    deeplink {
        path("/truyen/..*")
    }
}

dependencies {
    implementation(project(":lib:imagedecoder"))
    implementation(project(":lib:synchrony"))
}
