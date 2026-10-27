package keiyoushi.lib.cronetruntime;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import android.util.Log;
import com.google.android.gms.common.GooglePlayServicesUtilLight;
import com.google.android.gms.net.CronetProviderInstaller;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;

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
    if (use(context, "HttpEngine-Native-Provider")) return;
    readyBy = SystemClock.elapsedRealtime() + WAIT_MILLIS;
    try {
      // The app's manifest has no Play services version tag
      GooglePlayServicesUtilLight.enableUsingApkIndependentContext();
      Executor executor = r -> new Thread(r, "KeiCronet").start();
      CronetProviderInstaller.installProvider(context)
          .addOnSuccessListener(
              executor,
              unused -> {
                if (!use(context, "Google-Play-Services-Cronet-Provider")) {
                  Log.i(TAG, "No Cronet, staying on OkHttp");
                }
                ready.countDown();
              })
          .addOnFailureListener(
              executor,
              e -> {
                Log.i(TAG, "Play services Cronet unavailable, staying on OkHttp", e);
                ready.countDown();
              });
    } catch (Throwable t) {
      Log.e(TAG, "Play services Cronet", t);
      ready.countDown();
    }
  }

  /** Never throws */
  private static boolean use(Context context, String name) {
    try {
      for (CronetProvider provider : CronetProvider.getAllProviders(context)) {
        if (provider.getName().equals(name) && provider.isEnabled()) {
          CronetEngine engine =
              provider
                  .createBuilder()
                  .enableHttp2(true)
                  .enableQuic(false)
                  .enableBrotli(true)
                  .build();
          server = new CronetServer(engine);
          Log.i(TAG, "Using " + name + " " + engine.getVersionString());
          return true;
        }
      }
    } catch (Throwable t) {
      Log.e(TAG, name + " failed, staying on OkHttp", t);
    }
    return false;
  }

  /** Cronet finds its providers through the context's class loader */
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
