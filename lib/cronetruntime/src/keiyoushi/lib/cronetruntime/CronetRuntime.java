package keiyoushi.lib.cronetruntime;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import org.chromium.net.CronetEngine;

/** Entry point for lib:cronet */
public final class CronetRuntime {
  private static final String TAG = "KeiyoushiCronet";
  private static final long WAIT_MILLIS = 5_000;

  private static volatile Interceptor server;
  private static boolean started;
  private static long readyBy;
  private static final CountDownLatch ready = new CountDownLatch(1);

  private CronetRuntime() {}

  /**
   * Sends one request over the device's Cronet; null until Cronet is ready or when the device has
   * none.
   */
  public static Interceptor server(Context context) {
    if (server == null) {
      start(new LoaderContext(context.getApplicationContext()));
      // Requests made before Play services' Cronet is ready wait for it, briefly
      long wait = readyBy - SystemClock.elapsedRealtime();
      if (wait > 0) {
        try {
          ready.await(wait, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
    return server;
  }

  private static synchronized void start(Context context) {
    if (started) return;
    started = true;
    if (HttpEngineCronet.isAvailable()) {
      try {
        use(new HttpEngineCronet(context), "HttpEngine");
        return;
      } catch (Throwable t) {
        Log.e(TAG, "HttpEngine failed", t);
      }
    }
    readyBy = SystemClock.elapsedRealtime() + WAIT_MILLIS;
    try {
      new Thread(
              () -> {
                try {
                  use(
                      PlayServicesCronet.createBuilder(context)
                          .enableHttp2(true)
                          .enableQuic(false)
                          .enableBrotli(true)
                          .build(),
                      "Play services");
                } catch (Throwable t) {
                  Log.i(TAG, "Play services Cronet unavailable, staying on OkHttp", t);
                }
                ready.countDown();
              },
              "KeiCronet")
          .start();
    } catch (Throwable t) {
      Log.e(TAG, "Play services Cronet failed, staying on OkHttp", t);
      ready.countDown();
    }
  }

  private static void use(CronetEngine engine, String name) {
    server = new CronetServer(engine);
    Log.i(TAG, "Using " + name + " " + engine.getVersionString());
  }

  /** Play services' Cronet module finds the Cronet API through the context's class loader */
  private static final class LoaderContext extends ContextWrapper {
    LoaderContext(Context base) {
      super(base);
    }

    @Override
    public Context getApplicationContext() {
      return this;
    }

    @Override
    public ClassLoader getClassLoader() {
      return CronetRuntime.class.getClassLoader();
    }
  }
}
