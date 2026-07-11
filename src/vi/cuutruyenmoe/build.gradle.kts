import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CuuTruyen (unoriginal)"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://cuutruyen.moe"
    }
}
