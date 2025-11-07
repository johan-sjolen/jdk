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

#include "classfile/skipAnnotations.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/bytes.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

AnnotationParser AnnotationParser::skip(int n_bytes) const {
  if (st == Fail) return fail();
  if (limit - n_bytes <= pos) {
    return fail();
  } else {
    return AnnotationParser{buf, pos + n_bytes, limit, st, nevp, nv};
  }
}

AnnotationParser AnnotationParser::read_u2(u2& out) const {
  if (st == Fail) return fail();
  if (limit - 2 <= pos) {
    return fail();
  }
  out = Bytes::get_Java_u2((address)&buf[pos]);
  return skip(2);
}

AnnotationParser AnnotationParser::read_u1(u1& out) const {
  if (st == Fail) return fail();
  if (limit - 1 <= pos) {
    return fail();
  }
  out = buf[pos];
  return skip(1);
}

AnnotationParser AnnotationParser::transition_to(State next_state) const {
  if (st == Fail) return fail();
  return AnnotationParser{buf, pos, limit, next_state, nevp, nv};
}

AnnotationParser AnnotationParser::transition_to(State next_state, u2 v) const {
  if (st == Fail) return fail();
  if (next_state == ArrayValue) return AnnotationParser{buf, pos, limit, next_state, nevp, v};
  if (next_state == ElementValuePair) return AnnotationParser{buf, pos, limit, next_state, v, nv};
  ShouldNotReachHere();
}

AnnotationParser AnnotationParser::fail() const {
  return AnnotationParser{buf, limit, limit, Fail, 0, 0};
}

AnnotationParser AnnotationParser::done() const {
  return transition_to(Done);
}

AnnotationParser AnnotationParser::array_value(u2 num_values) const {
  return transition_to(ArrayValue, num_values);
}

AnnotationParser AnnotationParser::element_value_pair(u2 num_evp) const {
  return transition_to(ElementValuePair, num_evp);
}

AnnotationParser AnnotationParser::annotation() const {
  return transition_to(ElementValuePair);
}

bool AnnotationParser::has_failed() {
  return st == Fail;
}

AnnotationParser AnnotationParser::parse_element_value() const {
  AnnotationParser p = *this;
  u1 tag;
  p = p.read_u1(tag);
  switch (tag) {
    case 'B':
    case 'C':
    case 'I':
    case 'S':
    case 'Z':
    case 'D':
    case 'F':
    case 'J':
    case 'c':
    case 's':
      return p.skip(2).done();
      break;
    case 'e':
      return p.skip(4).done();
      break;
    case '[': {
      u2 num_values;
      p = p.read_u2(num_values);
      return p.array_value(num_values);
    } break;
    case '@':
      return p.annotation();
      break;
    default:
      // invalid tag
      return p.fail();
  }
}

bool AnnotationParser::small_step(GrowableArray<AnnotationParser>& stack, int& parsed_pos) const {
  AnnotationParser p = *this;
  if (p.st == Annotation) {
    u2 type_index, num_element_value_pairs;
    p = p.read_u2(type_index);
    p = p.read_u2(num_element_value_pairs);
    stack.push(p.element_value_pair(num_element_value_pairs));
    parsed_pos = p.pos;
    return true;
  } else if (p.st == ElementValuePair) {
    if (p.nevp == 0) {
      stack.push(p.done());
      parsed_pos = p.pos;
      return true;
    }
    p.nevp--;
    AnnotationParser recur = p.skip(2).parse_element_value();
    stack.push(p);
    stack.push(recur);
    parsed_pos = recur.pos;
    return true;
  } else if (p.st == ArrayValue) {
    if (p.nv == 0) {
      stack.push(p.done());
      parsed_pos = p.pos;
      return true;
    } else {
      p.nv--;
      AnnotationParser recur = p.parse_element_value();
      stack.push(p);
      stack.push(recur);
      parsed_pos = recur.pos;
      return true;
    }
  } else if (p.st == Done) {
    parsed_pos = p.pos;
    return true;
  } else if (p.st == Fail) {
    return false;
  } else {
    ShouldNotReachHere();
  }
}

int AnnotationParser::skip_annotation(const u1* buf, int limit, int pos) {
  ResourceMark rm;
  GrowableArray<AnnotationParser> stack;
  stack.push(AnnotationParser{buf, pos, limit, AnnotationParser::Annotation, 0, 0});
  int current_pos = pos;
  while (!stack.is_empty()) {
    AnnotationParser p = stack.pop();
    p.pos = current_pos;
    bool success = p.small_step(stack, current_pos);
    if (!success) {
      return limit;
    }
  }
  return current_pos;
}
