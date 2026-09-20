import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dm5"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        name = "动漫屋"

        baseUrl {
            mirrors(
                "https://www.dm5.com",
                "https://www.dm5.cn",
            )
        }
    }

    deeplink {
        host("www.dm5.com")
        host("www.dm5.cn")
        host("m.dm5.com")
        path("/manhua-..*")
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}
