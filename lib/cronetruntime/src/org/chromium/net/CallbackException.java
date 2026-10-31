// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

public abstract class CallbackException extends CronetException {
  protected CallbackException(String message, Throwable cause) {
    super(message, cause);
  }
}
