// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.util.concurrent.RejectedExecutionException;

public final class InlineExecutionProhibitedException extends RejectedExecutionException {
  public InlineExecutionProhibitedException() {
    super("Inline execution is prohibited for this request");
  }
}
