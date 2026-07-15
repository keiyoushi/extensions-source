import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ariverse"
    versionCode = 53
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://arigl.xyz")
        }
        id = 4480433466073326866
    }
}
