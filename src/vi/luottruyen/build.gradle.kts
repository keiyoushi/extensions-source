import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuotTruyen"
    versionCode = 11
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://luottruyen16.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
