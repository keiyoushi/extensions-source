import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HotComics"
    pkgName = "en.hotcomics"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    deeplink {
        path("/en/..*/..*\\.html")
        path("/de/..*/..*\\.html")
    }

    listOf("en", "de").forEach {
        source {
            lang = it
            baseUrl = "https://hotcomics.io"
        }
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
