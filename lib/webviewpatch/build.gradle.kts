plugins {
    alias(kei.plugins.library)
}

// The public API (stripRequestedWith) and the WebViewPatches contract only. The patch itself — the ELF
// locator, the native-memory write and the self-test — lives in the Keiyoushi Runtime, reached through this
// module's generated RuntimeModules.webviewpatch.
keiyoushiRuntime {
    requires("webviewpatch", api = 1)
}

dependencies {
    implementation(libs.keiyoushi.runtime.api.webviewpatch)
}
