## 1.3.4

- Refactor and make multisrc
- Chapter page list now requires only 1 network request (those fetched in old versions still need 2)

## 1.3.3

- Appended `.to` to extension name
- Replaced dependencies
  - `android.net.Uri` → `okhttp3.HttpUrl`
  - `org.json` → `kotlinx.serialization`
- Refactored some code to separate files
- Image quality preference: added prompt to summary and made it take effect without restart, fixes [#12504](https://github.com/tachiyomiorg/tachiyomi-extensions/issues/12504)
- Added preference to show additional entries in volumes in list results and added code to support volumes, fixes [#12573](https://github.com/tachiyomiorg/tachiyomi-extensions/issues/12573)
- Improved parsing
  - Added code to parse authors and artists
  - Improved chapter list parsing
  - Other improvements
  - Performance boosts in selectors
- Added French, Korean and Chinese languages
- Corrected filter note type (Text → Header)
- Rewrote image descrambler
  - Used fragment in URL instead of appending error-prone query parameter, hopefully fixes [#12722](https://github.com/tachiyomiorg/tachiyomi-extensions/issues/12722)
  - Made interceptor singleton to be shared across languages
  - Simplified code logic to make it a lot more readable, thanks to Vetle in [#9325 (comment)](https://github.com/tachiyomiorg/tachiyomi-extensions/pull/9325#issuecomment-1100950110) for code reference
  - Used `javax.crypto.Cipher` for ARC4
  - Memoize permutation result to reduce calculation
  - Save as compressed JPG instead of PNG to avoid size bloat (original image is already compressed)

## 1.2.2

- Fixes filters causing manga list to fail to load.

## 1.2.1

- Builds on original PR and unscrambles the images.
