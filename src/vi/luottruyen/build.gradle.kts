import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuotTruyen"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://luottruyen17.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
