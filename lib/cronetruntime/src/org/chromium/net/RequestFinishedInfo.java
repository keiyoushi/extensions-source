// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.util.concurrent.Executor;

/** Play services' Cronet module collects Metrics for every request */
public abstract class RequestFinishedInfo {
  public abstract static class Listener {
    private final Executor mExecutor;

    public Listener(Executor executor) {
      if (executor == null) {
        throw new IllegalStateException("Executor must not be null");
      }
      mExecutor = executor;
    }

    public abstract void onRequestFinished(RequestFinishedInfo requestInfo);

    public Executor getExecutor() {
      return mExecutor;
    }
  }

  public abstract static class Metrics {}
}
