// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.io.IOException;

public abstract class CronetException extends IOException {
  protected CronetException(String message, Throwable cause) {
    super(message, cause);
  }
}
