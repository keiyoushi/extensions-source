// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

public abstract class NetworkException extends CronetException {
  protected NetworkException(String message, Throwable cause) {
    super(message, cause);
  }
}
