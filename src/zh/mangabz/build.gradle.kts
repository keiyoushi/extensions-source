import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabz"
    versionCode = 14
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

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

    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:unpacker"))
}
