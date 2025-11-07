/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "classfile/skipAnnotations.hpp"
#include "unittest.hpp"

template<typename T, int size>
void test(T(&buffer)[size], bool should_fail = false) {
  int limit = size + 1;
  int final_pos = AnnotationParser::skip_annotation(buffer, limit, 0);
  if (should_fail) {
    EXPECT_EQ(limit, final_pos);
  } else {
    EXPECT_GT(limit, final_pos);
  }
}

TEST(SkipAnnotations, ParseZeroEVPairs) {
  u1 buffer[] = {
    0xAA, 0xBB, // type_index (arbitrary, it's not used)
    0x00, 0x00  // nevp = 0
  };
  test(buffer);
}

TEST(SkipAnnotations, FewerEVPairsThanSpecifiedShouldFail) {
  u1 buffer[] = {
    0xAA, 0xBB, // type_index (arbitrary, it's not used)
    0x01, 0x00,  // nevp = 256
    0x00, 0x00, // e n i
    'B', // tag
    0x00, 0x00 // c v i
  };
  test(buffer, true);
}

TEST(SkipAnnotations, ParseOneEVPair) {
  u1 buffer[] = {
    0xAA, 0xBB, // type_index
    0x00, 0x01,  // nevp = 1
    0x00, 0x00, // e n i
    'B', // tag
    0x00, 0x00 // c v i
  };
  test(buffer);
}

TEST(SkipAnnotations, ParseWithSomeBracketRecursion) {
  u1 buffer[] = {
    0xAA, 0xBB, // type_index
    0x00, 0x01,  // nevp = 1
    0x00, 0x00, // e n i
    '[', // tag
    0x03, 0x00, // n v
    'e', // tag
    0x00, 0x00, // t n i
    0x00, 0x00, // c n i
    'e', // tag
    0x00, 0x00, // t n i
    0x00, 0x00, // c n i
    'e', // tag
    0x00, 0x00, // t n i
    0x00, 0x00 // c n i
  };
  test(buffer);

}
