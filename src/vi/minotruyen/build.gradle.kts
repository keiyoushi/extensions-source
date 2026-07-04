plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MinoTruyen"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "MinoTruyen Manga"
        lang = "vi"
        baseUrl("https://minotruyenv5.xyz") {
            withCustom = true
        }
    }

    source {
        name = "MinoTruyen Comics"
        lang = "vi"
        baseUrl("https://minotruyenv5.xyz") {
            withCustom = true
        }
    }

    source {
        name = "MinoTruyen Hentai"
        lang = "vi"
        baseUrl("https://minotruyenv5.xyz") {
            withCustom = true
        }
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
