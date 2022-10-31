/*
 * JarClassFilter.java
 *
 * Copyright (c) 2022 Joerg Delker
 *
 * This source code is based on Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */
package com.strobel.decompiler;

import com.strobel.core.StringUtilities;
import java.util.Arrays;
import java.util.List;

/**
 * Simple list prefix filter.
 *
 */
public class JarClassFilter {

  private final List<String> _filterList;

  /**
   * Initialize new JarClassFilter.
   *
   * @param csv List of class prefixes, separated by commas (e.g.
   * "org/json,com.sun"). If this is null or empty, all classes will match.
   */
  public JarClassFilter(String csv) {
    if (StringUtilities.isNullOrEmpty(csv)) {
      _filterList = List.of();
    } else {
      _filterList = Arrays.asList(csv.split(","));
    }
  }

  public boolean hasFilter() {
    return !_filterList.isEmpty();
  }

  /**
   * Determine if the given class name matches any prefix.
   *
   * @param name Class name
   * @return true, if class name matches any prefix or if no prefixes are
   * present.
   */
  public boolean matches(String name) {
    for (String prefix : _filterList) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
