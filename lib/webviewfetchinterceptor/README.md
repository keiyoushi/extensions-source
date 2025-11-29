# WebView Fetch Interceptor

An OkHttp interceptor that executes HTTP requests through a WebView using JavaScript `fetch` API. This is useful for bypassing certain protections or when you need to execute requests in a browser context.

The interceptor works by loading a lightweight file from your domain in a WebView, then executing JavaScript `fetch` calls within that context. This approach provides several advantages:

- **Browser context**: Requests execute in a real browser environment, bypassing certain protections
- **JavaScript execution**: Can execute JavaScript code via `evaluateJavascript` after page load
- **CORS avoidance**: Loading from the same domain avoids Cross-Origin Resource Sharing issues
- **Fast loading**: Using lightweight files (like `robots.txt`) ensures quick initialization

## Features

- Executes requests via WebView using JavaScript `fetch`
- Supports GET and POST requests with body
- Automatically handles headers and User-Agent
- Uses base64 encoding for binary response data
- Optional filter function to control which requests to intercept
- Configurable load URL for WebView context

## Usage

### Basic Usage

```kotlin
import eu.kanade.tachiyomi.lib.webviewfetchinterceptor.WebViewFetchInterceptor

override val client = network.client.newBuilder()
    .addInterceptor(WebViewFetchInterceptor(baseUrl))
    .build()
```

### With Filter Function (Recommended)

```kotlin
override val client = network.client.newBuilder()
    .addInterceptor(
        WebViewFetchInterceptor(
            loadUrl = "$baseUrl/robots.txt", // Lightweight file from same domain
            filter = { request ->
                // Only intercept requests from the same domain to avoid CORS issues
                request.url.toString().startsWith(baseUrl)
            }
        )
    )
    .build()
```

**Best Practice**: Use a lightweight file from your `baseUrl` (like `/robots.txt` or `/favicon.ico`) and filter requests to only intercept those from the same domain. This ensures fast loading and avoids CORS problems.

### Parameters

- `loadUrl` (String): The URL to load in the WebView to establish context before executing the fetch. **Important**: For best results, load a lightweight file from the same `baseUrl` domain (e.g., `/favicon.ico`, `/robots.txt`, or a small CSS file). This ensures:
  - Fast loading time
  - Same domain context, avoiding CORS issues
  - Proper JavaScript execution context via `evaluateJavascript`
  
  You can combine this with the `filter` parameter to only intercept requests from the same domain.

- `filter` (Function<Request, Boolean>?, optional): A function that determines which requests should be intercepted. Returns `true` to intercept, `false` to proceed normally. If not provided, all requests are intercepted. Recommended to filter by domain to avoid CORS issues.

## How It Works

1. The interceptor checks if the request should be intercepted using the filter function
2. If intercepted, it opens a WebView and loads the specified `loadUrl` (preferably a lightweight file from the same domain)
3. Once the page loads, it executes a JavaScript `fetch` with the original request details using `evaluateJavascript`
4. The response is encoded in base64 and sent back via a JavaScript interface
5. The interceptor decodes the response and builds an OkHttp Response

## Notes

- The WebView is destroyed after the request completes (with a 1-second delay)
- Default timeout is 30 seconds - adjust if needed for slower networks
- All request bodies are converted to strings (UTF-8)
- Response bodies are encoded/decoded using base64 to support binary content
- JavaScript execution happens on the main thread via Handler
- The interceptor is thread-safe (`@Synchronized`)

