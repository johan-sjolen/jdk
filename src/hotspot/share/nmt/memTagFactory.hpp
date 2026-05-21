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
o *
 */

#include "classfile/altHashing.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "nmt/memTag.hpp"
#include "nmt/nmtLocker.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/growableArray.hpp"

#include <stdint.h>
#include <type_traits>

#ifndef SHARE_NMT_MEMTAGFACTORY_HPP
#define SHARE_NMT_MEMTAGFACTORY_HPP


// NameToTagTable is a closed hashing hash table.
// We don't expect MemTag creation or lookup to be a common operation, so we focus
// on minimal memory usage.
class MemTagFactory : AllStatic {
public:
  class Instance {
  private:
    using MemTagI = std::underlying_type_t<MemTag>;
    using EntryRef = std::underlying_type_t<MemTag>;
    constexpr static const EntryRef Nil = std::numeric_limits<EntryRef>::max();

    static constexpr const auto nr_of_buckets = 128;

    static MemTagI index(MemTag tag) {
      return static_cast<MemTagI>(tag);
    }

    struct Entry {
      MemTag tag;
      EntryRef next;
      const char* name;
      Entry(MemTag tag, EntryRef next, const char* name)
        : tag(tag),
          next(next), name(name) {}
      Entry() : next(Nil) {}
    };

    GrowableArrayCHeap<Entry, mtNMT> _entries;
    const int _table_size;
    EntryRef* _table;
    GrowableArrayCHeap<const char*, mtNMT> _human_readable_names;
    const uint64_t _seed;
    volatile int _number_of_tags;

    void put_tag(MemTag tag, const char* name);

  public:
    uint32_t string_hash(const char* t) const;
    constexpr static const MemTag AbsentTag = static_cast<MemTag>(std::numeric_limits<std::underlying_type_t<MemTag>>::max());

    Instance();
    const char* name_of(MemTag tag) const;
    const char* human_readable_name_of(MemTag memtag) const;
    MemTag tag_or_absent(const char* name);
    int number_of_tags() const;

    MemTag tag(const char* name, const char* human_name = nullptr);
    void set_human_readable_name_of(MemTag tag, const char* hrn);
    bool is_enum_name(const char* name, MemTag* out) const;
    const char* enum_name_of(MemTag tag) const;
  };

private:
  static DeferredStatic<Instance> _instance;
  void assert_valid_enum_tag_length();

public:
  constexpr static const MemTag AbsentTag = Instance::AbsentTag;
  // The maximum size of a tag name.
  static const int maximum_tag_length = 1024;

  static void initialize() {
    NmtMemTagLocker nvml;
    _instance.initialize();
  }
  static MemTag tag(const char* name) {
    NmtMemTagLocker nvml;
    return _instance->tag(name);
  }

  static const char* name_of(MemTag tag) {
    NmtMemTagLocker nvml;
    return _instance->name_of(tag);
  }

  static const char* human_readable_name_of(MemTag tag) {
    NmtMemTagLocker nvml;
    return _instance->human_readable_name_of(tag);
  }

  static void set_human_readable_name_of(MemTag tag, const char* hrn) {
    NmtMemTagLocker nvml;
    return _instance->set_human_readable_name_of(tag, hrn);
  }

  static int number_of_tags() {
    return _instance->number_of_tags();
  }

  static MemTag tag_or_absent(const char* name) {
    NmtMemTagLocker ntml;
    return _instance->tag_or_absent(name);
  }

  static bool is_enum_name(const char* name, MemTag* out) {
    NmtMemTagLocker ntml;
    return _instance->is_enum_name(name, out);
   }

   static const char* enum_name_of(MemTag tag) {
     NmtMemTagLocker ntml;
     return _instance->enum_name_of(tag);
   }
};

#endif // SHARE_NMT_MEMTAGFACTORY_HPP
