// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

public abstract class ExperimentalUrlRequest extends UrlRequest {
  public abstract static class Builder extends UrlRequest.Builder {
    @Override
    public abstract Builder setHttpMethod(String method);

    @Override
    public abstract ExperimentalUrlRequest build();
  }
}
