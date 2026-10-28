// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.nio.ByteBuffer;

public abstract class BidirectionalStream {
  public abstract void start();

  public abstract void read(ByteBuffer buffer);

  public abstract void write(ByteBuffer buffer, boolean endOfStream);

  public abstract void flush();

  public abstract void cancel();

  public abstract boolean isDone();
}
