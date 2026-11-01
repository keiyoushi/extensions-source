package keiyoushi.lib.cronetruntime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.http.HttpEngine;
import android.net.http.HttpException;
import android.os.Build;
import android.os.ext.SdkExtensions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataSink;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

/** The platform's HttpEngine behind the Cronet API, as far as CronetServer uses it */
@SuppressLint("NewApi") // Only built when isAvailable()
final class HttpEngineCronet extends CronetEngine {
  private final HttpEngine engine;

  HttpEngineCronet(Context context) {
    engine =
        new HttpEngine.Builder(context)
            .setEnableHttp2(true)
            .setEnableQuic(false)
            .setEnableBrotli(true)
            .build();
  }

  /** On Android 14, and on 12 and 13 once their Tethering module ships it */
  static boolean isAvailable() {
    return Build.VERSION.SDK_INT >= 31
        && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7;
  }

  @Override
  public String getVersionString() {
    return HttpEngine.getVersionString();
  }

  @Override
  public void shutdown() {
    engine.shutdown();
  }

  @Override
  public UrlRequest.Builder newUrlRequestBuilder(
      String url, UrlRequest.Callback callback, Executor executor) {
    Request request = new Request(callback);
    android.net.http.UrlRequest.Builder builder =
        engine.newUrlRequestBuilder(url, executor, request.callback);
    return new UrlRequest.Builder() {
      @Override
      public UrlRequest.Builder setHttpMethod(String method) {
        builder.setHttpMethod(method);
        return this;
      }

      @Override
      public UrlRequest.Builder addHeader(String header, String value) {
        builder.addHeader(header, value);
        return this;
      }

      @Override
      public UrlRequest.Builder setUploadDataProvider(
          UploadDataProvider provider, Executor executor) {
        builder.setUploadDataProvider(new Upload(provider), executor);
        return this;
      }

      @Override
      public UrlRequest build() {
        request.request = builder.build();
        return request;
      }
    };
  }

  private static final class Request extends UrlRequest {
    private android.net.http.UrlRequest request;
    private final android.net.http.UrlRequest.Callback callback;

    Request(UrlRequest.Callback callback) {
      this.callback =
          new android.net.http.UrlRequest.Callback() {
            @Override
            public void onRedirectReceived(
                android.net.http.UrlRequest r,
                android.net.http.UrlResponseInfo info,
                String newLocationUrl)
                throws Exception {
              callback.onRedirectReceived(Request.this, new Info(info), newLocationUrl);
            }

            @Override
            public void onResponseStarted(
                android.net.http.UrlRequest r, android.net.http.UrlResponseInfo info)
                throws Exception {
              callback.onResponseStarted(Request.this, new Info(info));
            }

            @Override
            public void onReadCompleted(
                android.net.http.UrlRequest r,
                android.net.http.UrlResponseInfo info,
                ByteBuffer buffer)
                throws Exception {
              callback.onReadCompleted(Request.this, new Info(info), buffer);
            }

            @Override
            public void onSucceeded(
                android.net.http.UrlRequest r, android.net.http.UrlResponseInfo info) {
              callback.onSucceeded(Request.this, new Info(info));
            }

            @Override
            public void onFailed(
                android.net.http.UrlRequest r,
                android.net.http.UrlResponseInfo info,
                HttpException error) {
              callback.onFailed(
                  Request.this, info == null ? null : new Info(info), new Failure(error));
            }

            @Override
            public void onCanceled(
                android.net.http.UrlRequest r, android.net.http.UrlResponseInfo info) {
              callback.onCanceled(Request.this, info == null ? null : new Info(info));
            }
          };
    }

    @Override
    public void start() {
      request.start();
    }

    @Override
    public void followRedirect() {
      request.followRedirect();
    }

    @Override
    public void read(ByteBuffer buffer) {
      request.read(buffer);
    }

    @Override
    public void cancel() {
      request.cancel();
    }
  }

  private static final class Info extends UrlResponseInfo {
    private final android.net.http.UrlResponseInfo info;

    Info(android.net.http.UrlResponseInfo info) {
      this.info = info;
    }

    @Override
    public int getHttpStatusCode() {
      return info.getHttpStatusCode();
    }

    @Override
    public String getHttpStatusText() {
      return info.getHttpStatusText();
    }

    @Override
    public List<Map.Entry<String, String>> getAllHeadersAsList() {
      return info.getHeaders().getAsList();
    }

    @Override
    public String getNegotiatedProtocol() {
      return info.getNegotiatedProtocol();
    }
  }

  private static final class Failure extends CronetException {
    Failure(HttpException cause) {
      super(cause.getMessage(), cause);
    }
  }

  private static final class Upload extends android.net.http.UploadDataProvider {
    private final UploadDataProvider provider;

    Upload(UploadDataProvider provider) {
      this.provider = provider;
    }

    @Override
    public long getLength() throws IOException {
      return provider.getLength();
    }

    @Override
    public void read(android.net.http.UploadDataSink sink, ByteBuffer buffer) throws IOException {
      provider.read(new Sink(sink), buffer);
    }

    @Override
    public void rewind(android.net.http.UploadDataSink sink) throws IOException {
      provider.rewind(new Sink(sink));
    }

    @Override
    public void close() throws IOException {
      provider.close();
    }
  }

  private static final class Sink extends UploadDataSink {
    private final android.net.http.UploadDataSink sink;

    Sink(android.net.http.UploadDataSink sink) {
      this.sink = sink;
    }

    @Override
    public void onReadSucceeded(boolean finalChunk) {
      sink.onReadSucceeded(finalChunk);
    }

    @Override
    public void onReadError(Exception exception) {
      sink.onReadError(exception);
    }

    @Override
    public void onRewindSucceeded() {
      sink.onRewindSucceeded();
    }

    @Override
    public void onRewindError(Exception exception) {
      sink.onRewindError(exception);
    }
  }
}
