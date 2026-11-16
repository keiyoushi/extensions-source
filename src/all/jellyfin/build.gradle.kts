import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jellyfin"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "http://127.0.0.1:8096"
    }
}

dependencies {
    // The archive reader the app ships for its local source
    compileOnly("me.zhanghai.android.libarchive:library:1.1.7")
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")
}
