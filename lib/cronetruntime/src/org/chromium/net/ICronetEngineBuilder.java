// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

public abstract class ICronetEngineBuilder {
  public abstract ICronetEngineBuilder enableHttp2(boolean value);

  public abstract ICronetEngineBuilder enableQuic(boolean value);

  public ICronetEngineBuilder enableBrotli(boolean value) {
    // Do nothing for older implementations.
    return this;
  }

  public abstract ExperimentalCronetEngine build();
}
