// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import java.util.List;
import java.util.Map;

public abstract class UrlResponseInfo {
  public abstract static class HeaderBlock {}

  public abstract int getHttpStatusCode();

  public abstract String getHttpStatusText();

  public abstract List<Map.Entry<String, String>> getAllHeadersAsList();

  public abstract String getNegotiatedProtocol();
}
