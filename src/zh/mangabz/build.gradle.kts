import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabz"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl {
            mirrors(
                "https://mangabz.com",
                "https://xmanhua.com",
                "https://yymanhua.com",
            )
        }
    }

    deeplink {
        host("mangabz.com")
        host("xmanhua.com")
        host("yymanhua.com")
        path("/..*")
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}
