import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YuriNeko"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://yurinekoz.com"
        id = 4413681066613655890L
    }

    deeplink {
        path("/manga/.*")
    }
}
