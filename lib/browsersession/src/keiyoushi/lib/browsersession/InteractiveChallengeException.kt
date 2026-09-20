package keiyoushi.lib.browsersession

import java.io.IOException

/**
 * Thrown when an interactive challenge (such as Cloudflare Turnstile checkbox, captcha, or puzzle)
 * is detected during headless challenge resolution.
 *
 * Prompts the user to open the target source in WebView for manual human verification instead of
 * hanging for a long timeout window.
 */
class InteractiveChallengeException(
    message: String = "Interactive challenge detected; please open in WebView to complete verification",
) : IOException(message)
