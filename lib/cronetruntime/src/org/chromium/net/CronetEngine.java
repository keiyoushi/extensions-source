// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.util.concurrent.Executor;

public abstract class CronetEngine {
  public static class Builder {
    public abstract static class LibraryLoader {
      public abstract void loadLibrary(String libName);
    }

    protected final ICronetEngineBuilder mBuilderDelegate;

    public Builder(ICronetEngineBuilder builderDelegate) {
      mBuilderDelegate = builderDelegate;
    }

    public Builder enableQuic(boolean value) {
      mBuilderDelegate.enableQuic(value);
      return this;
    }

    public Builder enableHttp2(boolean value) {
      mBuilderDelegate.enableHttp2(value);
      return this;
    }

    public Builder enableBrotli(boolean value) {
      mBuilderDelegate.enableBrotli(value);
      return this;
    }

    public CronetEngine build() {
      return mBuilderDelegate.build();
    }
  }

  public abstract String getVersionString();

  public abstract void shutdown();

  public abstract UrlRequest.Builder newUrlRequestBuilder(
      String url, UrlRequest.Callback callback, Executor executor);
}
