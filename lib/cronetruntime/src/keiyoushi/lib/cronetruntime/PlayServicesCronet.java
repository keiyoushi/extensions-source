package keiyoushi.lib.cronetruntime;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import dalvik.system.DelegateLastClassLoader;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Locale;
import org.chromium.net.CronetEngine;
import org.chromium.net.ICronetEngineBuilder;

/**
 * Play services' Cronet module, loaded the way play-services-basement's DynamiteModule does, so the
 * runtime needn't ship Play services' client libraries.
 */
final class PlayServicesCronet {
  private static final String GMS = "com.google.android.gms";
  private static final String CHIMERA = "com.google.android.gms.chimera";
  private static final String MODULE = "com.google.android.gms.cronet_dynamite";
  private static final String GMS_CERT_SHA256 =
      "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83";

  // IDynamiteLoaderV2, in the module APK the chimera provider points at
  private static final String V2_LOADER = "com.google.android.gms.dynamiteloader.DynamiteLoaderV2";
  private static final String V2_DESCRIPTOR = "com.google.android.gms.dynamite.IDynamiteLoaderV2";
  private static final int V2_LOAD_MODULE = 2;
  private static final int V2_LOAD_MODULE_NO_CRASH_UTILS = 3;

  // The oldest module verified with the trimmed API; a module's native code aborts the process if
  // a class it registers can't load
  private static final int MIN_CRONET_MAJOR = 67;

  private PlayServicesCronet() {}

  /** Throws when Play services or its Cronet module isn't there */
  static CronetEngine.Builder createBuilder(Context context) throws Exception {
    verifyGms(context);
    Context module = loadModule(context);
    if (module == null) throw new IllegalStateException("No Cronet module context");
    ClassLoader loader = module.getClassLoader();
    String version =
        (String)
            loader
                .loadClass("org.chromium.net.impl.ImplVersion")
                .getMethod("getCronetVersion")
                .invoke(null);
    if (Integer.parseInt(version.split("\\.")[0]) < MIN_CRONET_MAJOR) {
      throw new IllegalStateException("Play services Cronet " + version + " is too old");
    }
    ICronetEngineBuilder impl =
        (ICronetEngineBuilder)
            loader
                .loadClass("org.chromium.net.impl.NativeCronetEngineBuilderImpl")
                .getConstructor(Context.class)
                .newInstance(context);
    return new CronetEngine.Builder(impl);
  }

  /** Play services' code runs in our process, so it must be Google's */
  private static void verifyGms(Context context) throws Exception {
    PackageManager pm = context.getPackageManager();
    @SuppressWarnings("deprecation")
    PackageInfo info = pm.getPackageInfo(GMS, PackageManager.GET_SIGNATURES);
    @SuppressWarnings("deprecation")
    android.content.pm.Signature[] signatures = info.signatures;
    if (signatures == null
        || signatures.length != 1
        || !GMS_CERT_SHA256.equals(sha256(signatures[0].toByteArray()))) {
      throw new SecurityException("Play services isn't signed by Google");
    }
    ApplicationInfo app = info.applicationInfo;
    if (!app.enabled) throw new IllegalStateException("Play services disabled");
    // A user-installed Play services needs Google's older module loader, which this doesn't do
    if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        == 0) {
      throw new IllegalStateException("Play services isn't a system app");
    }
    ProviderInfo provider = pm.resolveContentProvider(CHIMERA, 0);
    if (provider == null || !GMS.equals(provider.packageName)) {
      throw new SecurityException("Chimera provider isn't Play services'");
    }
  }

  private static Context loadModule(Context context) throws Exception {
    Cursor cursor = query(context);
    try {
      if (flag(cursor, "disableStandaloneDynamiteLoader2", 1)) {
        throw new IllegalStateException("Play services disabled its standalone module loader");
      }
      int version = cursor.getInt(0);
      String loaderPath = cursor.getString(2);
      int code = flag(cursor, "loaderVersion", 2) ? V2_LOAD_MODULE_NO_CRASH_UTILS : V2_LOAD_MODULE;
      // The loaders play-services-basement uses
      ClassLoader loader =
          Build.VERSION.SDK_INT >= 29
              ? new DelegateLastClassLoader(loaderPath, ClassLoader.getSystemClassLoader())
              : new ModuleClassLoader(loaderPath, ClassLoader.getSystemClassLoader());
      IBinder binder = (IBinder) loader.loadClass(V2_LOADER).getConstructor().newInstance();
      Parcel data = Parcel.obtain();
      data.writeInterfaceToken(V2_DESCRIPTOR);
      data.writeStrongBinder(new Wrapper(context.getApplicationContext()));
      data.writeString(MODULE);
      data.writeInt(version);
      data.writeStrongBinder(new Wrapper(cursor));
      return transact(binder, code, data, r -> (Context) Wrapper.unwrap(r.readStrongBinder()));
    } finally {
      cursor.close();
    }
  }

  /** The module's row from the chimera provider, copied so the provider can be let go */
  private static Cursor query(Context context) throws Exception {
    Uri uri =
        new Uri.Builder()
            .scheme("content")
            .authority(CHIMERA)
            .path("api")
            .appendPath(MODULE)
            .appendQueryParameter("requestStartUptime", String.valueOf(SystemClock.uptimeMillis()))
            .build();
    try (Cursor remote = context.getContentResolver().query(uri, null, null, null, null)) {
      if (remote == null || !remote.moveToFirst() || remote.getInt(0) <= 0) {
        throw new IllegalStateException("No Cronet module in Play services");
      }
      MatrixCursor copy = new MatrixCursor(remote.getColumnNames(), 1);
      Object[] row = new Object[remote.getColumnCount()];
      for (int i = 0; i < row.length; i++) {
        switch (remote.getType(i)) {
          case Cursor.FIELD_TYPE_INTEGER:
            row[i] = remote.getLong(i);
            break;
          case Cursor.FIELD_TYPE_FLOAT:
            row[i] = remote.getDouble(i);
            break;
          case Cursor.FIELD_TYPE_STRING:
            row[i] = remote.getString(i);
            break;
          case Cursor.FIELD_TYPE_BLOB:
            row[i] = remote.getBlob(i);
            break;
          default:
            row[i] = null;
        }
      }
      copy.addRow(row);
      copy.moveToFirst();
      return copy;
    }
  }

  private static boolean flag(Cursor cursor, String column, int atLeast) {
    int index = cursor.getColumnIndex(column);
    return index >= 0 && cursor.getInt(index) >= atLeast;
  }

  private interface Reply<T> {
    T read(Parcel reply) throws Exception;
  }

  private static <T> T transact(IBinder binder, int code, Parcel data, Reply<T> read)
      throws Exception {
    Parcel reply = Parcel.obtain();
    try {
      binder.transact(code, data, reply, 0);
      reply.readException();
      return read.read(reply);
    } finally {
      data.recycle();
      reply.recycle();
    }
  }

  private static String sha256(byte[] bytes) throws Exception {
    StringBuilder hex = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
      hex.append(String.format(Locale.ROOT, "%02x", b));
    }
    return hex.toString();
  }

  /** Child-first, except for the platform's classes */
  private static final class ModuleClassLoader extends PathClassLoader {
    ModuleClassLoader(String path, ClassLoader parent) {
      super(path, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith("java.") && !name.startsWith("android.")) {
        try {
          return findClass(name);
        } catch (ClassNotFoundException ignored) {
          // Not in the module
        }
      }
      return super.loadClass(name, resolve);
    }
  }

  /**
   * play-services-basement's ObjectWrapper: across class loaders, Play services reads the one
   * private field reflectively
   */
  private static final class Wrapper extends Binder {
    private final Object value;

    Wrapper(Object value) {
      this.value = value;
      // Android 8's queryLocalInterface dereferences the descriptor
      attachInterface(null, "com.google.android.gms.dynamic.IObjectWrapper");
    }

    static Object unwrap(IBinder binder) throws IllegalAccessException {
      if (binder == null) return null;
      for (Field field : binder.getClass().getDeclaredFields()) {
        if (!field.isSynthetic()) {
          field.setAccessible(true);
          return field.get(binder);
        }
      }
      return null;
    }
  }
}
