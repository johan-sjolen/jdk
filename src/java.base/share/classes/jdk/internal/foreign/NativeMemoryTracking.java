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
 */

package jdk.internal.foreign;

import java.lang.OutOfMemoryError;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySegment.Scope;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.ArenaImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.misc.Unsafe;

/**
 * The entrypoint to the static NMT interface.
 */
public class NativeMemoryTracking {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final int addressSize = UNSAFE.addressSize();
    private static final boolean nmtEnabled;

    private static final JavaLangAccess JLACCESS = SharedSecrets.getJavaLangAccess();

    private static native long makeTag(String name);
    private static native long allocate0(long size, long mem_tag);
    private static native long reallocate0(long pointer, long size, long mem_tag);
    private static native boolean isNMTEnabled();
    private static native void registerNatives();


    static {
        runtimeSetup();
        nmtEnabled = isNMTEnabled();
    }

    public static long reallocate(long pointer, long size) throws OutOfMemoryError {
        return reallocate0(pointer, size, currentTag());
    }

    public static long allocate(long size) {
        return allocate0(size, currentTag());
    }

    public static final class Tag {
        private long tag;
        Tag(long tag) { this.tag = tag; }
    }

    public static Tag resolveName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Invalid name, cannot be null");
        }
        if (!nmtEnabled) {
            return new Tag(currentTag());
        }
        long tag = makeTag(name);
        if (tag == -1) {
            throw new IllegalArgumentException();
        }
        return new Tag(tag);
    }

    // Get the current tag as a long
    private static long currentTag() {
        return JLACCESS.nativeMemoryTrackingTag();
    }


    // Replaces the old tag with `newTag` and returns the old one.
    public static Tag getAndSet(Tag newTag) {
        long tag = JLACCESS.nativeMemoryTrackingTag();
        JLACCESS.setNativeMemoryTrackingTag(newTag.tag);
        return new Tag(tag);
    }

    public static void with(Tag tag, Runnable action) {
        Tag oldTag = getAndSet(tag);
        try {
            action.run();
        } finally {
            getAndSet(oldTag);
        }
    }

    private static void runtimeSetup() {
        registerNatives();
    }
}
