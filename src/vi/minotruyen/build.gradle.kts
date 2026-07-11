import io.github.keiyoushi.gradle.api.ContentWarning

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
        baseUrl {
            custom("https://minotruyenv5.xyz")
        }
    }

    source {
        name = "MinoTruyen Comics"
        lang = "vi"
        baseUrl {
            custom("https://minotruyenv5.xyz")
        }
    }

    source {
        name = "MinoTruyen Hentai"
        lang = "vi"
        baseUrl {
            custom("https://minotruyenv5.xyz")
        }
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
