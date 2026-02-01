/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery

interface AppLifecycleProvider {
  var isAppInForeground: Boolean
}

class GalleryLifecycleProvider : AppLifecycleProvider {
  private var _isAppInForeground = false

  override var isAppInForeground: Boolean
    get() = _isAppInForeground
    set(value) {
      _isAppInForeground = value
    }
}
