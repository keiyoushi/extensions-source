// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 * Cronet's API from org.chromium.net:cronet-api 141.7340.3, trimmed to what the runtime and Play
 * services' Cronet module use, including the supertypes of the classes the module's native code
 * registers. The module links against these classes, so what's kept must keep its exact signatures.
 */
package org.chromium.net;
