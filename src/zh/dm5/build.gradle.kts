import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dm5"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

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
}

dependencies {
    implementation(project(":lib:unpacker"))
}
