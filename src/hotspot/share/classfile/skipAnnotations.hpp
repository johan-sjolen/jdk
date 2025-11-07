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
 *
 */

#include "utilities/globalDefinitions.hpp"

template<typename E>
class GrowableArray;

// Annotations in the classfile are defined to have a mutually recursive schema (see JVMS§4.7.16).
// Parsing of this structure is done on the Java-side, for the most part, but certain annotations are parsed by the VM.
// So, regardless, we have to parse this structure.
// We do so in a recursive-descent manner, but where we have reified the stack.
// We define a few simple parsers, which small_step uses to produce the next step in the parsing.
// The skip_annotation function becomes our driving function, setting up the stack and initial parsing context.
struct AnnotationParser {
  // Recursive points to-be-parsed
  enum State {
    Annotation,
    ElementValuePair,
    ArrayValue,
    Done, // No further parsing to be done by this frame
    Fail // Parsing failed
  };

  const u1* buf;
  int pos;   // Parse position
  int limit; // Cannot parse to this position (oob)
  State st; // The state of the parser -- what type of data we're going to parse next
  u2 nevp;  // Used only if st == ElementValuePair (tracks remaining number of ev pairs)
  u2 nv;    // Used only if st == ArrayValue (tracks remaining number of values)

private:
  AnnotationParser skip(int n_bytes) const;
  AnnotationParser read_u2(u2& out) const;
  AnnotationParser read_u1(u1& out) const;

  AnnotationParser transition_to(State next_state) const;
  AnnotationParser transition_to(State next_state, u2 v) const;
  AnnotationParser fail() const;
  AnnotationParser done() const;
  AnnotationParser array_value(u2 num_values) const;
  AnnotationParser element_value_pair(u2 num_evp) const;
  AnnotationParser annotation() const;
  AnnotationParser parse_element_value() const;

  bool has_failed() const;

  AnnotationParser small_step(GrowableArray<AnnotationParser>& stack, int& parsed_pos) const;
public:
  static int skip_annotation(const u1* buf, int limit, int pos);
};

