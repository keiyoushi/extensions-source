// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

public abstract class ExperimentalBidirectionalStream extends BidirectionalStream {
  public abstract static class Builder extends BidirectionalStream.Builder {
    @Override
    public abstract Builder setHttpMethod(String method);

    @Override
    public abstract Builder addHeader(String header, String value);

    @Override
    public abstract Builder setPriority(int priority);

    @Override
    public abstract Builder delayRequestHeadersUntilFirstFlush(
        boolean delayRequestHeadersUntilFirstFlush);

    @Override
    public Builder addRequestAnnotation(Object annotation) {
      return this;
    }

    @Override
    public Builder setTrafficStatsTag(int tag) {
      return this;
    }

    @Override
    public Builder setTrafficStatsUid(int uid) {
      return this;
    }

    @Override
    public abstract ExperimentalBidirectionalStream build();
  }
}
