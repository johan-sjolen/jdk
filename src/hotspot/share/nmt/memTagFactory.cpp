/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/macros.hpp"

#if INCLUDE_JFR
#include "jfr/recorder/checkpoint/types/jfrType.hpp"
#endif

#include "nmt/memTagFactory.hpp"
#include "nmt/nmtCommon.hpp"
#include "utilities/stringUtils.hpp"

DeferredStatic<MemTagFactory::Instance> MemTagFactory::_instance;

uint32_t MemTagFactory::Instance::string_hash(const char* t) const {
  return AltHashing::halfsiphash_32(_seed, t, (int)strlen(t));
}

void MemTagFactory::Instance::put_tag(MemTag tag, const char* name) {
  assert(strlen(name) < maximum_tag_length, "mustn't be ridiculously long");
  int bucket = string_hash(name) % _table_size;
  const char* name_copy = os::strdup(name, mtNMT);
  Entry nentry(tag, _table[bucket], name_copy);
  _entries.push(nentry);
  _table[bucket] = _entries.length() - 1;
  AtomicAccess::inc(&_number_of_tags);
}

MemTag MemTagFactory::Instance::tag_or_absent(const char* name) {
  int bucket = string_hash(name) % _table_size;
  EntryRef link = _table[bucket];
  while (link != Nil) {
    Entry e = _entries.at(link);
    if (strcmp(e.name, name) == 0) {
      return e.tag;
    }
    link = e.next;
  }
  return AbsentTag;
}

MemTag MemTagFactory::Instance::tag(const char* name, const char* human_name) {
  MemTag found = AbsentTag;
  if (is_enum_name(name, &found)) {
    // If it's an enum tag, return the existing tag
    return found;
  }
  found = tag_or_absent(name);
  if (found != AbsentTag) {
    return found;
  }
  if (std::numeric_limits<MemTagI>::max() == static_cast<MemTagI>(_number_of_tags)) {
    // Out of MemTags, revert to mtOther
    return mtOther;
  }

  // No tag found, have to create a new one
  MemTag i = static_cast<MemTag>(_number_of_tags);
  put_tag(i, name);
  if (human_name != nullptr) {
    set_human_readable_name_of(i, human_name);
  }
  // Register type with JFR
  JFR_ONLY(NMTTypeConstant::register_single_type(i, name);)
  return i;
}

const char* MemTagFactory::Instance::name_of(MemTag tag) const {
  if (index(tag) >= AtomicAccess::load(&_number_of_tags)) {
    return nullptr;
  }
  if (index(tag) < NMTUtil::number_of_enum_tags()) {
    return human_readable_name_of(tag);
  }
  return _entries.at(index(tag)).name;
}

void MemTagFactory::Instance::set_human_readable_name_of(MemTag tag, const char* hrn) {
  assert(strlen(hrn) < maximum_tag_length, "must be");
  MemTagI i = index(tag);
  const char* copy = os::strdup(hrn);
  const char*& ref = _human_readable_names.at_grow(i, nullptr);
  assert(ref == nullptr, "do not reassign human-readable names");
  ref = copy;
}

const char* MemTagFactory::Instance::human_readable_name_of(MemTag tag) const {
  MemTagI i = index(tag);
  if (i < _human_readable_names.length()) {
    return _human_readable_names.at(index(tag));
  }
  return nullptr;
}

// We use this for our seed
static constexpr size_t BigPrime = 5000002429;

MemTagFactory::Instance::Instance()
    : _entries(), _table_size(nr_of_buckets), _table(nullptr),
      _human_readable_names(), _seed(BigPrime), _number_of_tags(0) {
  _table = NEW_C_HEAP_ARRAY(EntryRef, _table_size, mtNMT);
  for (int i = 0; i < _table_size; i++) {
    _table[i] = Nil;
  }
#define MEMORY_TAG_ADD_TO_TABLE(mem_tag, name) this->tag(#mem_tag, name);
MEMORY_TAG_DO(MEMORY_TAG_ADD_TO_TABLE)
#undef MEMORY_TAG_ADD_TO_TABLE
}

int MemTagFactory::Instance::number_of_tags() const {
  return AtomicAccess::load(&_number_of_tags);
}

bool MemTagFactory::Instance::is_enum_name(const char* option, MemTag* out) const {
  for (int i = 0; i < _entries.length(); i++) {
    const char* enum_name = _entries.at(i).name;
    const char* enum_name_without_mt = enum_name + 2; // skip "mt" prefix
    const char* hr_name = nullptr;
    if (_human_readable_names.length() > i) {
      hr_name = _human_readable_names.at(i);
    }
    if ((hr_name != nullptr && ::strcasecmp(hr_name, option) == 0) ||
        ::strcasecmp(enum_name, option) == 0                       ||
        ::strcasecmp(enum_name_without_mt, option) == 0) {
      *out = (MemTag)i;
      return true;
    }
  }
  return false;
}

const char* MemTagFactory::Instance::enum_name_of(MemTag tag) const {
  MemTagI i = index(tag);
  if (i < NMTUtil::number_of_enum_tags()) {
    return _entries.at(i).name;
  }
  return nullptr;
}

void MemTagFactory::assert_valid_enum_tag_length() {
  assert(NMTUtil::number_of_enum_tags() <= _instance->number_of_tags(), "must be");
}
