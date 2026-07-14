import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
	alias(kei.plugins.extension)
}

keiyoushi {
	name = "ToonLivre"
	theme = "madara"
	versionCode = 1
	contentWarning = ContentWarning.SAFE
	libVersion = "1.4"

	source {
		lang = "pt"
		baseUrl = "https://toonlivre.net"
	}
}
