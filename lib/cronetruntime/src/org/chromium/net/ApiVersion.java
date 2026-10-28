// Copyright 2014 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

/** Read by Play services' Cronet module to tell which API it runs against */
public class ApiVersion {
  // The module calls only what this API level has; below version 59 or level 30 it would also call
  // builder methods this trimmed API leaves out
  private static final String CRONET_VERSION = "141.0.7340.3";
  private static final int API_LEVEL = 38;
  private static final int MIN_COMPATIBLE_API_LEVEL = 3;

  private ApiVersion() {}

  public static int getMaximumAvailableApiLevel() {
    return API_LEVEL;
  }

  public static int getApiLevel() {
    return MIN_COMPATIBLE_API_LEVEL;
  }

  public static String getCronetVersion() {
    return CRONET_VERSION;
  }
}
