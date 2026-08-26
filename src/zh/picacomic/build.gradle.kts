import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Picacomic"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "哔咔漫画"
        lang = "zh"
        baseUrl = "https://manhuabika.com"
    }

    deeplink {
        path("/comic/..*")
    }
}
