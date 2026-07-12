import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team Lanh Lung"
    versionCode = 33
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "Team Lạnh Lùng"
        lang = "vi"
        baseUrl {
            custom("https://icecoldcore.com")
        }
    }
}
