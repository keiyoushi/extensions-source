plugins {
    alias(kei.plugins.library)
}

dependencies {
    // The app provides Kotlin; webkit's one androidx.core reference is in this lib, its annotations aren't bundled
    implementation(libs.androidx.webkit) { isTransitive = false }
}
