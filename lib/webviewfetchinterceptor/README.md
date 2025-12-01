# WebView Fetch Interceptor

An OkHttp interceptor that executes HTTP requests through a WebView using JavaScript `fetch` API. This is useful for bypassing certain protections (like Cloudflare Turnstile) or when you need to execute requests in a browser context.

The interceptor works by establishing a WebView context (optionally loading a specific URL), then executing JavaScript `fetch` calls within that context. This approach provides several advantages:

- **Browser context**: Requests execute in a real browser environment, bypassing certain protections
- **TLS/SSL signatures**: Uses browser TLS/SSL stack, making requests indistinguishable from legitimate browser traffic
- **JavaScript execution**: Can execute JavaScript code via `evaluateJavascript` after page load
- **CORS avoidance**: Same domain context avoids Cross-Origin Resource Sharing issues

## Features

- Executes requests via WebView using JavaScript `fetch`
- Supports GET and POST requests with body
- Automatically handles headers and User-Agent
- Uses base64 encoding for binary response data
- Optional filter function to control which requests to intercept
- Optional load URL for special cases requiring specific domain context
- Configurable timeout (default: 60 seconds)

## Installation

Add the library to your extension's `build.gradle`:

```gradle
dependencies {
    implementation project(':lib:webviewfetchinterceptor')
}
```

## Usage

### Basic Usage (Recommended)

In most cases, you don't need to specify `loadUrl`. The interceptor will automatically use the request's domain as the base URL:

```kotlin
import eu.kanade.tachiyomi.lib.webviewfetchinterceptor.WebViewFetchInterceptor

override val client = network.client.newBuilder()
    .addInterceptor(
        WebViewFetchInterceptor(
            filter = { request ->
                // Only intercept requests from the same domain to avoid CORS issues
                request.url.toString().startsWith(baseUrl)
            }
        )
    )
    .build()
```

### With LoadUrl (Special Cases Only)

Only use `loadUrl` when you need a specific URL/domain context for the fetch. For example, when you need to load a specific page to establish cookies or session state:

```kotlin
override val client = network.client.newBuilder()
    .addInterceptor(
        WebViewFetchInterceptor(
            filter = { request ->
                // Only intercept requests from the same domain to avoid CORS issues
                request.url.toString().startsWith(baseUrl)
            },
            loadUrl = "$baseUrl/robots.txt" // Only in special cases
        )
    )
    .build()
```

**Note**: In most cases, you don't need `loadUrl`. The interceptor automatically uses the request's domain as the base URL context.

### Parameters

- `filter` (Function<Request, Boolean>?, optional): A function that determines which requests should be intercepted. Returns `true` to intercept, `false` to proceed normally. If `null`, all requests are intercepted. **Recommended**: Filter by domain to only intercept requests from the same domain to avoid CORS issues.

- `timeout` (Long, optional): Timeout in seconds for waiting for the WebView response. Default is 60 seconds.

- `loadUrl` (String?, optional): **Only use in special cases** when you need a specific URL/domain context for the fetch. In most cases, this should be `null` (default). The interceptor will automatically use the request's domain as the base URL.
  
  If you need to provide a specific URL (e.g., to establish cookies or session state), use a lightweight file from the same `baseUrl` domain:
  - `/robots.txt` (recommended - very lightweight)
  - `/favicon.ico` (small image file)
  - A small CSS file
  
  This ensures fast loading, same domain context (avoiding CORS), and proper JavaScript execution.

## How It Works

1. The interceptor checks if the request should be intercepted using the filter function
2. If intercepted, it opens a WebView and establishes context:
   - If `loadUrl` is provided, loads that URL
   - Otherwise, uses the request's domain as base URL with empty HTML content
3. Once the context is established, it executes a JavaScript `fetch` with the original request details using `evaluateJavascript`
4. The response is encoded in base64 and sent back via a JavaScript interface
5. The interceptor decodes the response and builds an OkHttp Response

## Notes

- The WebView is destroyed after the request completes (with a 1-second delay)
- Default timeout is 60 seconds - can be customized via the `timeout` parameter
- All request bodies are converted to strings (UTF-8)
- Response bodies are encoded/decoded using base64 to support binary content
- JavaScript execution happens on the main thread via Handler
- The interceptor is thread-safe (`@Synchronized`)
- In most cases, `loadUrl` is not needed - the interceptor automatically uses the request's domain

