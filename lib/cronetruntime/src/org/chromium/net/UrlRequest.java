// Copyright 2014 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

public abstract class UrlRequest {
  public abstract static class Builder {
    public static final int REQUEST_PRIORITY_IDLE = 0;
    public static final int REQUEST_PRIORITY_LOWEST = 1;
    public static final int REQUEST_PRIORITY_LOW = 2;
    public static final int REQUEST_PRIORITY_MEDIUM = 3;
    public static final int REQUEST_PRIORITY_HIGHEST = 4;

    public abstract Builder setHttpMethod(String method);

    public abstract Builder addHeader(String header, String value);

    public abstract Builder setPriority(int priority);

    public abstract Builder setUploadDataProvider(
        UploadDataProvider uploadDataProvider, Executor executor);

    public abstract UrlRequest build();
  }

  public abstract static class Callback {
    public abstract void onRedirectReceived(
        UrlRequest request, UrlResponseInfo info, String newLocationUrl) throws Exception;

    public abstract void onResponseStarted(UrlRequest request, UrlResponseInfo info)
        throws Exception;

    public abstract void onReadCompleted(
        UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) throws Exception;

    public abstract void onSucceeded(UrlRequest request, UrlResponseInfo info);

    public abstract void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error);

    public void onCanceled(UrlRequest request, UrlResponseInfo info) {}
  }

  public abstract static class StatusListener {
    public abstract void onStatus(int status);
  }

  public abstract void start();

  public abstract void followRedirect();

  public abstract void read(ByteBuffer buffer);

  public abstract void cancel();
}
