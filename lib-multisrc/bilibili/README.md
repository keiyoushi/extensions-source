# Bilibili

Table of Content
- [FAQ](#FAQ)
  - [Why are some chapters missing?](#why-are-some-chapters-missing)
- [Guides](#Guides)
  - [Reading already paid chapters](#reading-already-paid-chapters)

Don't find the question you are looking for? Go check out our general FAQs and Guides
over at [Extension FAQ] or [Getting Started].

[Extension FAQ]: https://tachiyomi.org/help/faq/#extensions
[Getting Started]: https://tachiyomi.org/help/guides/getting-started/#installation

## FAQ

### Why are some chapters missing?

Bilibili now have series with paid chapters. These will be filtered out from
the chapter list by default if you didn't buy it before or if you're not signed in.
To sign in with your existing account, follow the guide available above.

## Guides

### Reading already paid chapters

The **Bilibili Comics** sources allows the reading of paid chapters in your account.
Follow the following steps to be able to sign in and get access to them:

1. Open the popular or latest section of the source.
2. Open the WebView by clicking the button with a globe icon.
3. Do the login with your existing account *(read the observations section)*.
4. Close the WebView and refresh the chapter list of the titles
   you want to read the already paid chapters.

#### Observations

- Sign in with your Google account is not supported due to WebView restrictions
  access that Google have. **You need to have a simple account in order to be able
  to login via WebView**.
- You may sometime face the *"Failed to refresh the token"* error. To fix it,
  you just need to open the WebView, await for the website to completely load.
  After that, you can close the WebView and try again.
- The extension **will not** bypass any payment requirement. You still do need
  to buy the chapters you want to read or wait until they become available and
  added to your account.
