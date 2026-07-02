plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MinoTruyen"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "MinoTruyen Manga"
        lang = "vi"
        baseUrl("https://minotruyenv5.xyz") {
            withCustom.set(true)
        }
    }

    source {
        name = "MinoTruyen Comics"
        lang = "vi"
        baseUrl("https://minotruyenv5.xyz") {
            withCustom.set(true)
        }
    }

    source {
        name = "MinoTruyen Hentai"
        lang = "vi"
        baseUrl("https://minotruyenv5.xyz") {
            withCustom.set(true)
        }
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
