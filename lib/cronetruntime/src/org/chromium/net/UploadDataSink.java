// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

public abstract class UploadDataSink {
  public abstract void onReadSucceeded(boolean finalChunk);

  public abstract void onReadError(Exception exception);

  public abstract void onRewindSucceeded();

  public abstract void onRewindError(Exception exception);
}
