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

#include "jni.h"
#include "nmt/mallocHeader.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/memTagFactory.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/jniHandles.inline.hpp"

#include <limits>

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

JVM_ENTRY(jlong, NMT_makeTag(JNIEnv *env, jobject ignored_this, jobject tag_name_string)) {
  constexpr static int max_tagname_len = 1024;
  if (!MemTracker::enabled()) {
    return (jlong)mtNone;
  }
  Handle tnh(Thread::current(), JNIHandles::resolve(tag_name_string));
  oop oop = tnh();

  // The tag name must be a non-null instance of java.lang.String
  // whose length doesn't exceed max_tagname_len.
  if (oop == nullptr) {
    return -1;
  }
  if (!oop->is_a(vmClasses::String_klass())) {
    return -1;
  }
  typeArrayOop value = java_lang_String::value(oop);
  int length = java_lang_String::length(oop, value);
  if (length > max_tagname_len) {
    return -1;
  }

  // OK, fine.
  ResourceMark rm;
  const char* tag_name = java_lang_String::as_utf8_string(oop);
  MemTag tag = MemTagFactory::tag(tag_name);
  return (jlong)tag;
}
JVM_END

JVM_ENTRY(jlong, NMT_allocate0(JNIEnv *env, jobject ignored_this, jlong size, jlong mem_tag)) {
  if (size < 0) {
    return 0;
  }
  bool enabled = MemTracker::enabled();
  if (enabled && (mem_tag < 0 || MemTagFactory::number_of_tags() <= mem_tag)) {
    return 0;
  }

#ifndef _LP64
  static_assert(sizeof(jlong) > sizeof(size_t), "must be");
  if (size > static_cast<jlong>(std::numeric_limits<size_t>::max())) {
    return 0;
  }
#endif

  MemTag tag = enabled ? (MemTag)mem_tag : mtOther;
  return (jlong)os::malloc(size, tag);
}
JVM_END

JVM_ENTRY(jboolean, NMT_isNMTEnabled(JNIEnv *env, jobject ignored_this)) {
  return (jboolean)MemTracker::enabled();
}
JVM_END

static JNINativeMethod NMT_methods[] = {
  {CC "makeTag", CC "(Ljava/lang/String;)J", FN_PTR(NMT_makeTag)},
  {CC "allocate0", CC "(JJ)J", FN_PTR(NMT_allocate0)},
  {CC "isNMTEnabled", CC "()Z", FN_PTR(NMT_isNMTEnabled)}
};

JVM_ENTRY(void, JVM_RegisterNativeMemoryTrackingMethods(JNIEnv *env, jclass NMT_class)) {
  ThreadToNativeFromVM ttnfv(thread);

  int status = env->RegisterNatives(NMT_class, NMT_methods, sizeof(NMT_methods) / sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionCheck(),
            "register java.lang.invoke.MethodHandleNative natives");
}
JVM_END
