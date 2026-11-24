import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DamCoNuong"
    versionCode = 22
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://damconuong.tech")
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
