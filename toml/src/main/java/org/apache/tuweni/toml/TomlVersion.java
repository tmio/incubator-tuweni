// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.toml;

import javax.annotation.Nullable;

/** Supported TOML specification versions. */
public enum TomlVersion {
  /**
   * The 0.4.0 version of TOML.
   *
   * <p>This specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.4.0.md">https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.4.0.md</a>.
   */
  V0_4_0(null, 0),
  /**
   * The 0.5.0 version of TOML.
   *
   * <p>This specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.5.0.md">https://github.com/toml-lang/toml/blob/master/versions/en/toml-v0.5.0.md</a>.
   */
  V0_5_0(null, 1),
  /** The latest stable specification of TOML. */
  LATEST(V0_5_0, V0_5_0.index),
  /**
   * The head (development) specification of TOML.
   *
   * <p>The latest specification can be found at <a href=
   * "https://github.com/toml-lang/toml/blob/master/README.md">https://github.com/toml-lang/toml/blob/master/README.md</a>.
   *
   * <p>Note: As the specification is under active development, this implementation may not match
   * the latest changes.
   */
  HEAD(null, 99);

  final TomlVersion canonical;

  final int index;

  TomlVersion(@Nullable TomlVersion canonical, int index) {
    this.canonical = canonical != null ? canonical : this;
    this.index = index;
  }

  boolean after(TomlVersion other) {
    return this.index > other.index;
  }
}
