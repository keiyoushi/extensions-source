// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.nio.ByteBuffer;

public abstract class BidirectionalStream {
  public abstract static class Builder {
    public abstract Builder setHttpMethod(String method);

    public abstract Builder addHeader(String header, String value);

    public abstract Builder setPriority(int priority);

    public abstract Builder delayRequestHeadersUntilFirstFlush(
        boolean delayRequestHeadersUntilFirstFlush);

    public Builder addRequestAnnotation(Object annotation) {
      return this;
    }

    public Builder setTrafficStatsTag(int tag) {
      return this;
    }

    public Builder setTrafficStatsUid(int uid) {
      return this;
    }

    public Builder bindToNetwork(long networkHandle) {
      return this;
    }

    public abstract BidirectionalStream build();
  }

  public abstract static class Callback {
    public abstract void onStreamReady(BidirectionalStream stream);

    public abstract void onResponseHeadersReceived(
        BidirectionalStream stream, UrlResponseInfo info);

    public abstract void onReadCompleted(
        BidirectionalStream stream, UrlResponseInfo info, ByteBuffer buffer, boolean endOfStream);

    public abstract void onWriteCompleted(
        BidirectionalStream stream, UrlResponseInfo info, ByteBuffer buffer, boolean endOfStream);

    public void onResponseTrailersReceived(
        BidirectionalStream stream, UrlResponseInfo info, UrlResponseInfo.HeaderBlock trailers) {}

    public abstract void onSucceeded(BidirectionalStream stream, UrlResponseInfo info);

    public abstract void onFailed(
        BidirectionalStream stream, UrlResponseInfo info, CronetException error);

    public void onCanceled(BidirectionalStream stream, UrlResponseInfo info) {}
  }

  public abstract void start();

  public abstract void read(ByteBuffer buffer);

  public abstract void write(ByteBuffer buffer, boolean endOfStream);

  public abstract void flush();

  public abstract void cancel();

  public abstract boolean isDone();
}
