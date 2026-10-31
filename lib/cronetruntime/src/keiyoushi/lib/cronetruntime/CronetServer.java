package keiyoushi.lib.cronetruntime;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.Call;
import okhttp3.CipherSuite;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.TlsVersion;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataSink;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

/** OkHttp's network call, over Cronet: sends one request as given, follows no redirects. */
final class CronetServer implements Interceptor {
  private static final ExecutorService EXECUTOR =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "KeiCronet");
            t.setDaemon(true);
            return t;
          });

  // Headers Cronet sets itself
  private static final Set<String> CRONET_HEADERS =
      new HashSet<>(
          Arrays.asList(
              "accept-encoding",
              "connection",
              "content-length",
              "host",
              "keep-alive",
              "proxy-connection",
              "transfer-encoding",
              "te",
              "upgrade"));

  // Encodings Cronet decodes, handing the body back without them
  private static final Set<String> DECODED =
      new HashSet<>(Arrays.asList("gzip", "x-gzip", "deflate", "br", "zstd"));

  private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

  // Placeholder handshake for OkHttp's cache
  private static final Handshake HANDSHAKE =
      Handshake.get(
          TlsVersion.TLS_1_3,
          CipherSuite.TLS_AES_128_GCM_SHA256,
          Collections.emptyList(),
          Collections.emptyList());

  private static final Object SUCCEEDED = new Object();

  private final CronetEngine engine;

  CronetServer(CronetEngine engine) {
    this.engine = engine;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    CompletableFuture<UrlResponseInfo> head = new CompletableFuture<>();
    // Read results, then SUCCEEDED or the failure
    BlockingQueue<Object> reads = new LinkedBlockingQueue<>();
    boolean[] redirected = {false};
    long sentAt = System.currentTimeMillis();

    UrlRequest.Callback callback =
        new UrlRequest.Callback() {
          @Override
          public void onRedirectReceived(UrlRequest r, UrlResponseInfo info, String location) {
            redirected[0] = true;
            head.complete(info);
            r.cancel();
          }

          @Override
          public void onResponseStarted(UrlRequest r, UrlResponseInfo info) {
            head.complete(info);
          }

          @Override
          public void onReadCompleted(UrlRequest r, UrlResponseInfo info, ByteBuffer buf) {
            reads.add(buf);
          }

          @Override
          public void onSucceeded(UrlRequest r, UrlResponseInfo info) {
            reads.add(SUCCEEDED);
          }

          @Override
          public void onFailed(UrlRequest r, UrlResponseInfo info, CronetException error) {
            head.completeExceptionally(error);
            reads.add(error);
          }

          @Override
          public void onCanceled(UrlRequest r, UrlResponseInfo info) {
            IOException canceled = new IOException("Canceled");
            head.completeExceptionally(canceled);
            reads.add(canceled);
          }
        };

    UrlRequest urlRequest;
    try {
      UrlRequest.Builder builder =
          engine
              .newUrlRequestBuilder(request.url().toString(), callback, EXECUTOR)
              .setHttpMethod(request.method());
      Headers headers = request.headers();
      for (int i = 0; i < headers.size(); i++) {
        if (!CRONET_HEADERS.contains(headers.name(i).toLowerCase(Locale.ROOT))) {
          builder.addHeader(headers.name(i), headers.value(i));
        }
      }
      RequestBody requestBody = request.body();
      if (requestBody != null) {
        Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        // Cronet refuses an upload without a Content-Type
        if (request.header("Content-Type") == null) {
          MediaType type = requestBody.contentType();
          builder.addHeader("Content-Type", (type != null ? type : OCTET_STREAM).toString());
        }
        builder.setUploadDataProvider(new BytesUpload(buffer.readByteArray()), EXECUTOR);
      }
      urlRequest = builder.build();
    } catch (RuntimeException e) {
      throw new IOException(e);
    }
    boolean listened = cancelWith(chain.call(), urlRequest);
    try {
      urlRequest.start();
    } catch (RuntimeException e) {
      throw new IOException(e);
    }

    long headTimeout =
        chain.readTimeoutMillis() > 0
            ? (long) chain.connectTimeoutMillis()
                + chain.writeTimeoutMillis()
                + chain.readTimeoutMillis()
            : 0;
    UrlResponseInfo info = await(chain.call(), urlRequest, head, headTimeout, listened);
    long receivedAt = System.currentTimeMillis();

    Response.Builder builder = head(request, info, sentAt, receivedAt);
    Response response = builder.build();
    String contentType = response.header("Content-Type");
    MediaType mediaType = contentType == null ? null : MediaType.parse(contentType);
    // Handed back as is, its body unread: Cronet won't read after a redirect
    if (redirected[0]) return builder.body(ResponseBody.create(new byte[0], mediaType)).build();
    String contentLength = response.header("Content-Length");
    CronetSource source =
        new CronetSource(chain.call(), urlRequest, reads, chain.readTimeoutMillis(), listened);
    return builder
        .body(
            new CronetBody(
                mediaType,
                contentLength == null ? -1 : parseLong(contentLength),
                Okio.buffer(source)))
        .build();
  }

  /** Waits for the response headers, with OkHttp's timeouts and cancellation */
  private static UrlResponseInfo await(
      Call call,
      UrlRequest urlRequest,
      CompletableFuture<UrlResponseInfo> head,
      long timeoutMillis,
      boolean listened)
      throws IOException {
    long deadline =
        timeoutMillis > 0
            ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            : Long.MAX_VALUE;
    try {
      while (true) {
        try {
          return head.get(waitMillis(listened, deadline), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          if (call.isCanceled()) {
            urlRequest.cancel();
            throw new IOException("Canceled");
          }
          if (System.nanoTime() > deadline) {
            urlRequest.cancel();
            throw new InterruptedIOException("timeout");
          }
        }
      }
    } catch (InterruptedException e) {
      urlRequest.cancel();
      Thread.currentThread().interrupt();
      throw new InterruptedIOException();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) throw (IOException) cause;
      throw new IOException(cause);
    }
  }

  /**
   * Cancels the Cronet request with the call, so waits needn't poll for it; false on OkHttp before
   * 5.4, which has no Call.addEventListener
   */
  private static boolean cancelWith(Call call, UrlRequest urlRequest) {
    try {
      call.addEventListener(
          new EventListener() {
            @Override
            public void canceled(Call c) {
              urlRequest.cancel();
            }
          });
    } catch (NoSuchMethodError | AbstractMethodError e) {
      return false;
    }
    if (call.isCanceled()) urlRequest.cancel();
    return true;
  }

  /** How long to block before checking the call and the clock again */
  private static long waitMillis(boolean listened, long deadlineNanos) {
    if (!listened) return 100;
    if (deadlineNanos == Long.MAX_VALUE) return 60_000;
    return Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()) + 1);
  }

  private static Response.Builder head(
      Request request, UrlResponseInfo info, long sentAt, long receivedAt) {
    boolean encoded = false;
    boolean decoded = true;
    for (Map.Entry<String, String> h : info.getAllHeadersAsList()) {
      if (h.getKey().equalsIgnoreCase("Content-Encoding")) {
        encoded = true;
        decoded &= isDecoded(h.getValue());
      }
    }
    decoded &= encoded;
    Headers.Builder headers = new Headers.Builder();
    for (Map.Entry<String, String> h : info.getAllHeadersAsList()) {
      String name = h.getKey();
      if (decoded
          && (name.equalsIgnoreCase("Content-Encoding") || name.equalsIgnoreCase("Content-Length")))
        continue;
      headers.addUnsafeNonAscii(name, h.getValue());
    }
    String message = info.getHttpStatusText();
    return new Response.Builder()
        .request(request)
        .protocol("h2".equals(info.getNegotiatedProtocol()) ? Protocol.HTTP_2 : Protocol.HTTP_1_1)
        .code(info.getHttpStatusCode())
        .message(message == null ? "" : message)
        .headers(headers.build())
        .handshake(request.isHttps() ? HANDSHAKE : null)
        .sentRequestAtMillis(sentAt)
        .receivedResponseAtMillis(receivedAt);
  }

  /** Every encoding in the list, stacked ones included, is one Cronet decodes */
  private static boolean isDecoded(String contentEncoding) {
    for (String encoding : contentEncoding.split(",")) {
      String name = encoding.trim().toLowerCase(Locale.ROOT);
      if (!name.isEmpty() && !name.equals("identity") && !DECODED.contains(name)) return false;
    }
    return true;
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static final class CronetBody extends ResponseBody {
    private final MediaType contentType;
    private final long contentLength;
    private final BufferedSource source;

    CronetBody(MediaType contentType, long contentLength, BufferedSource source) {
      this.contentType = contentType;
      this.contentLength = contentLength;
      this.source = source;
    }

    @Override
    public MediaType contentType() {
      return contentType;
    }

    @Override
    public long contentLength() {
      return contentLength;
    }

    @Override
    public BufferedSource source() {
      return source;
    }
  }

  /** The response body, read from Cronet one buffer at a time as the caller asks for it */
  private static final class CronetSource implements Source {
    private final Call call;
    private final UrlRequest urlRequest;
    private final BlockingQueue<Object> reads;
    private final long readTimeoutMillis;
    private final boolean listened;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
    private final Timeout timeout = new Timeout();
    private volatile boolean finished;

    CronetSource(
        Call call,
        UrlRequest urlRequest,
        BlockingQueue<Object> reads,
        long readTimeoutMillis,
        boolean listened) {
      this.call = call;
      this.listened = listened;
      this.urlRequest = urlRequest;
      this.reads = reads;
      this.readTimeoutMillis = readTimeoutMillis;
      buffer.limit(0);
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (!buffer.hasRemaining()) {
        if (finished) return -1;
        buffer.clear();
        urlRequest.read(buffer);
        Object result = next();
        if (result == SUCCEEDED) {
          finished = true;
          buffer.limit(0);
          return -1;
        }
        if (result instanceof IOException) {
          finished = true;
          buffer.limit(0);
          throw (IOException) result;
        }
        buffer.flip();
      }
      int count = (int) Math.min(byteCount, buffer.remaining());
      int limit = buffer.limit();
      buffer.limit(buffer.position() + count);
      sink.write(buffer);
      buffer.limit(limit);
      return count;
    }

    /** The next Cronet result, with OkHttp's read timeout and cancellation */
    private Object next() throws IOException {
      long readDeadline =
          readTimeoutMillis > 0
              ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)
              : Long.MAX_VALUE;
      try {
        while (true) {
          // The read timeout, or a deadline the caller set, e.g. OkHttp's cache discarding the rest
          long deadline =
              timeout.hasDeadline()
                  ? Math.min(readDeadline, timeout.deadlineNanoTime())
                  : readDeadline;
          Object result = reads.poll(waitMillis(listened, deadline), TimeUnit.MILLISECONDS);
          if (result != null) return result;
          if (call.isCanceled()) {
            finished = true;
            urlRequest.cancel();
            throw new IOException("Canceled");
          }
          if (System.nanoTime() > deadline) {
            finished = true;
            urlRequest.cancel();
            throw new InterruptedIOException("timeout");
          }
        }
      } catch (InterruptedException e) {
        finished = true;
        urlRequest.cancel();
        Thread.currentThread().interrupt();
        throw new InterruptedIOException();
      }
    }

    @Override
    public Timeout timeout() {
      return timeout;
    }

    // An unclosed body would keep its Cronet request open
    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() {
      close();
    }

    @Override
    public void close() {
      if (!finished) {
        finished = true;
        urlRequest.cancel();
      }
    }
  }

  private static final class BytesUpload extends UploadDataProvider {
    private final byte[] bytes;
    private int position;

    BytesUpload(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public long getLength() {
      return bytes.length;
    }

    @Override
    public void read(UploadDataSink sink, ByteBuffer buffer) {
      int count = Math.min(buffer.remaining(), bytes.length - position);
      buffer.put(bytes, position, count);
      position += count;
      sink.onReadSucceeded(false);
    }

    @Override
    public void rewind(UploadDataSink sink) {
      position = 0;
      sink.onRewindSucceeded();
    }
  }
}
