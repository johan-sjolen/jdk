/*
 * Copyright (c) 2022, red Hat, Inc. All rights reserved.
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


package org.openjdk.bench.java.lang;

import java.lang.ScopedValue.CallableOp;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import static org.openjdk.bench.java.lang.ScopedValuesData.*;

/**
 * Tests ScopedValue
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=4, time=1)
@Measurement(iterations=10, time=1)
@Threads(1)
@Fork(value = 1,
      jvmArgs = {"-Djmh.executor.class=org.openjdk.bench.java.lang.ScopedValuesExecutorService",
                        "-Djmh.executor=CUSTOM",
                        "-Djmh.blackhole.mode=COMPILER",
                        "--enable-preview"})
@State(Scope.Thread)
@SuppressWarnings("preview")
public class ScopedValues {

    private static final Integer THE_ANSWER = 42;

    // Test 1: make sure ScopedValue.get() is hoisted out of loops.

    @Benchmark
    public void thousandAdds_ScopedValue(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.sl1.get();
        }
        bh.consume(result);
    }

    @Benchmark
    public void thousandAdds_ThreadLocal(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.tl1.get();
        }
        bh.consume(result);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandIsBoundQueries(Blackhole bh) throws Exception {
        var result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.sl1.isBound() ? 1 : 0;
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandMaybeGets(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            if (ScopedValuesData.sl1.isBound()) {
                result += ScopedValuesData.sl1.get();
            }
        }
        return result;
    }

    // Test 2: stress the ScopedValue cache.
    // The idea here is to use a bunch of bound values cyclically, which
    // stresses the ScopedValue cache.

    int combine(int n, int i1, int i2, int i3, int i4, int i5, int i6) {
        return n + ((i1 ^ i2 >>> 6) + (i3 << 7) + i4 - i5 | i6);
    }

    @Benchmark
    public int sixValues_ScopedValue() throws Exception {
        int result = 0;
        for (int i = 0 ; i < 166; i++) {
            result = combine(result, sl1.get(), sl2.get(), sl3.get(), sl4.get(), sl5.get(), sl6.get());
        }
        return result;
    }

    @Benchmark
    public int sixValues_ThreadLocal() throws Exception {
        int result = 0;
        for (int i = 0 ; i < 166; i++) {
            result = combine(result, tl1.get(), tl2.get(), tl3.get(), tl4.get(), tl5.get(), tl6.get());
        }
        return result;
    }

    // Test 3: The cost of bind, then get
    // This is the worst case for ScopedValues because we have to create
    // a binding, link it in, then search the current bindings. In addition, we
    // create a cache entry for the bound value, then we immediately have to
    // destroy it.

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int CreateBindThenGetThenRemove_ScopedValue() throws Exception {
        return ScopedValue.where(sl1, THE_ANSWER).call(sl1::get);
    }


    // Create a Carrier ahead of time: might be slightly faster
    private static final ScopedValue.Carrier HOLD_42 = ScopedValue.where(sl1, 42);
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int bindThenGetThenRemove_ScopedValue() throws Exception {
        return HOLD_42.call(sl1::get);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int bindThenGetThenRemove_ThreadLocal() throws Exception {
        try {
            tl1.set(THE_ANSWER);
            return tl1.get();
        } finally {
            tl1.remove();
        }
    }

    // This has no exact equivalent in ScopedValue, but it's provided here for
    // information.
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int bindThenGetNoRemove_ThreadLocal() throws Exception {
        tl1.set(THE_ANSWER);
        return tl1.get();
    }

    // Test 4: The cost of binding, but not using any result
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Object bind_ScopedValue() throws Exception {
        return HOLD_42.call(aCallableOp);
    }
    private static final CallableOp<Class<?>, RuntimeException> aCallableOp = () -> ScopedValues.class;

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Object bind_ThreadLocal() throws Exception {
        try {
            tl1.set(THE_ANSWER);
            return this.getClass();
        } finally {
            tl1.remove();
        }
    }

    // Simply set a ThreadLocal so that the caller can see it
    // This has no exact equivalent in ScopedValue, but it's provided here for
    // information.
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void setNoRemove_ThreadLocal() throws Exception {
        tl1.set(THE_ANSWER);
    }

    // This is the closest I can think of to setNoRemove_ThreadLocal in that it
    // returns a value in a ScopedValue container. The container must already
    // be bound to an AtomicReference for this to work.
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void setNoRemove_ScopedValue() throws Exception {
        sl_atomicRef.get().setPlain(THE_ANSWER);
    }

    // Test 5: A simple counter

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void counter_ScopedValue() {
        sl_atomicInt.get().setPlain(
                sl_atomicInt.get().getPlain() + 1);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void counter_ThreadLocal() {
        // Very slow:
        // tl1.set(tl1.get() + 1);
        var ctr = tl_atomicInt.get();
        ctr.setPlain(ctr.getPlain() + 1);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void ScopedValuesAreFasT(Blackhole bh) {
        ScopedValue<Long> sv0 = ScopedValue.newInstance();
        ScopedValue<Long> sv1 = ScopedValue.newInstance();
        ScopedValue<Long> sv2 = ScopedValue.newInstance();
        ScopedValue<Long> sv3 = ScopedValue.newInstance();
        ScopedValue<Long> sv4 = ScopedValue.newInstance();
        ScopedValue<Long> sv5 = ScopedValue.newInstance();
        ScopedValue<Long> sv6 = ScopedValue.newInstance();
        ScopedValue<Long> sv7 = ScopedValue.newInstance();
        ScopedValue<Long> sv8 = ScopedValue.newInstance();
        ScopedValue<Long> sv9 = ScopedValue.newInstance();
        ScopedValue<Long> sv10 = ScopedValue.newInstance();
        ScopedValue<Long> sv11 = ScopedValue.newInstance();
        ScopedValue<Long> sv12 = ScopedValue.newInstance();
        ScopedValue<Long> sv13 = ScopedValue.newInstance();
        ScopedValue<Long> sv14 = ScopedValue.newInstance();
        ScopedValue<Long> sv15 = ScopedValue.newInstance();
        ScopedValue<Long> sv16 = ScopedValue.newInstance();
        ScopedValue<Long> sv17 = ScopedValue.newInstance();
        ScopedValue<Long> sv18 = ScopedValue.newInstance();
        ScopedValue<Long> sv19 = ScopedValue.newInstance();
        ScopedValue<Long> sv20 = ScopedValue.newInstance();
        ScopedValue<Long> sv21 = ScopedValue.newInstance();
        ScopedValue<Long> sv22 = ScopedValue.newInstance();
        ScopedValue<Long> sv23 = ScopedValue.newInstance();
        ScopedValue<Long> sv24 = ScopedValue.newInstance();
        ScopedValue<Long> sv25 = ScopedValue.newInstance();
        ScopedValue<Long> sv26 = ScopedValue.newInstance();
        ScopedValue<Long> sv27 = ScopedValue.newInstance();
        ScopedValue<Long> sv28 = ScopedValue.newInstance();
        ScopedValue<Long> sv29 = ScopedValue.newInstance();
        ScopedValue<Long> sv30 = ScopedValue.newInstance();
        ScopedValue<Long> sv31 = ScopedValue.newInstance();
        ScopedValue<Long> sv32 = ScopedValue.newInstance();
        ScopedValue<Long> sv33 = ScopedValue.newInstance();
        ScopedValue<Long> sv34 = ScopedValue.newInstance();
        ScopedValue<Long> sv35 = ScopedValue.newInstance();
        ScopedValue<Long> sv36 = ScopedValue.newInstance();
        ScopedValue<Long> sv37 = ScopedValue.newInstance();
        ScopedValue<Long> sv38 = ScopedValue.newInstance();
        ScopedValue<Long> sv39 = ScopedValue.newInstance();
        ScopedValue<Long> sv40 = ScopedValue.newInstance();
        ScopedValue<Long> sv41 = ScopedValue.newInstance();
        ScopedValue<Long> sv42 = ScopedValue.newInstance();
        ScopedValue<Long> sv43 = ScopedValue.newInstance();
        ScopedValue<Long> sv44 = ScopedValue.newInstance();
        ScopedValue<Long> sv45 = ScopedValue.newInstance();
        ScopedValue<Long> sv46 = ScopedValue.newInstance();
        ScopedValue<Long> sv47 = ScopedValue.newInstance();
        ScopedValue<Long> sv48 = ScopedValue.newInstance();
        ScopedValue<Long> sv49 = ScopedValue.newInstance();
        ScopedValue<Long> sv50 = ScopedValue.newInstance();
        ScopedValue<Long> sv51 = ScopedValue.newInstance();
        ScopedValue<Long> sv52 = ScopedValue.newInstance();
        ScopedValue<Long> sv53 = ScopedValue.newInstance();
        ScopedValue<Long> sv54 = ScopedValue.newInstance();
        ScopedValue<Long> sv55 = ScopedValue.newInstance();
        ScopedValue<Long> sv56 = ScopedValue.newInstance();
        ScopedValue<Long> sv57 = ScopedValue.newInstance();
        ScopedValue<Long> sv58 = ScopedValue.newInstance();
        ScopedValue<Long> sv59 = ScopedValue.newInstance();
        ScopedValue<Long> sv60 = ScopedValue.newInstance();
        ScopedValue<Long> sv61 = ScopedValue.newInstance();
        ScopedValue<Long> sv62 = ScopedValue.newInstance();
        ScopedValue<Long> sv63 = ScopedValue.newInstance();
        ScopedValue<Long> sv64 = ScopedValue.newInstance();
        ScopedValue<Long> sv65 = ScopedValue.newInstance();
        ScopedValue<Long> sv66 = ScopedValue.newInstance();
        ScopedValue<Long> sv67 = ScopedValue.newInstance();
        ScopedValue<Long> sv68 = ScopedValue.newInstance();
        ScopedValue<Long> sv69 = ScopedValue.newInstance();
        ScopedValue<Long> sv70 = ScopedValue.newInstance();
        ScopedValue<Long> sv71 = ScopedValue.newInstance();
        ScopedValue<Long> sv72 = ScopedValue.newInstance();
        ScopedValue<Long> sv73 = ScopedValue.newInstance();
        ScopedValue<Long> sv74 = ScopedValue.newInstance();
        ScopedValue<Long> sv75 = ScopedValue.newInstance();
        ScopedValue<Long> sv76 = ScopedValue.newInstance();
        ScopedValue<Long> sv77 = ScopedValue.newInstance();
        ScopedValue<Long> sv78 = ScopedValue.newInstance();
        ScopedValue<Long> sv79 = ScopedValue.newInstance();
        ScopedValue<Long> sv80 = ScopedValue.newInstance();
        ScopedValue<Long> sv81 = ScopedValue.newInstance();
        ScopedValue<Long> sv82 = ScopedValue.newInstance();
        ScopedValue<Long> sv83 = ScopedValue.newInstance();
        ScopedValue<Long> sv84 = ScopedValue.newInstance();
        ScopedValue<Long> sv85 = ScopedValue.newInstance();
        ScopedValue<Long> sv86 = ScopedValue.newInstance();
        ScopedValue<Long> sv87 = ScopedValue.newInstance();
        ScopedValue<Long> sv88 = ScopedValue.newInstance();
        ScopedValue<Long> sv89 = ScopedValue.newInstance();
        ScopedValue<Long> sv90 = ScopedValue.newInstance();
        ScopedValue<Long> sv91 = ScopedValue.newInstance();
        ScopedValue<Long> sv92 = ScopedValue.newInstance();
        ScopedValue<Long> sv93 = ScopedValue.newInstance();
        ScopedValue<Long> sv94 = ScopedValue.newInstance();
        ScopedValue<Long> sv95 = ScopedValue.newInstance();
        ScopedValue<Long> sv96 = ScopedValue.newInstance();
        ScopedValue<Long> sv97 = ScopedValue.newInstance();
        ScopedValue<Long> sv98 = ScopedValue.newInstance();
        ScopedValue<Long> sv99 = ScopedValue.newInstance();
        ScopedValue<String> MISSING = ScopedValue.newInstance();
        for (int i = 0; i < 100_000; i++) {
            bh.consume(MISSING.orElse("hello world"));
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void ScopedValuesAreSlow(Blackhole bh) {
        ScopedValue<Long> sv0 = ScopedValue.newInstance();
        ScopedValue<Long> sv1 = ScopedValue.newInstance();
        ScopedValue<Long> sv2 = ScopedValue.newInstance();
        ScopedValue<Long> sv3 = ScopedValue.newInstance();
        ScopedValue<Long> sv4 = ScopedValue.newInstance();
        ScopedValue<Long> sv5 = ScopedValue.newInstance();
        ScopedValue<Long> sv6 = ScopedValue.newInstance();
        ScopedValue<Long> sv7 = ScopedValue.newInstance();
        ScopedValue<Long> sv8 = ScopedValue.newInstance();
        ScopedValue<Long> sv9 = ScopedValue.newInstance();
        ScopedValue<Long> sv10 = ScopedValue.newInstance();
        ScopedValue<Long> sv11 = ScopedValue.newInstance();
        ScopedValue<Long> sv12 = ScopedValue.newInstance();
        ScopedValue<Long> sv13 = ScopedValue.newInstance();
        ScopedValue<Long> sv14 = ScopedValue.newInstance();
        ScopedValue<Long> sv15 = ScopedValue.newInstance();
        ScopedValue<Long> sv16 = ScopedValue.newInstance();
        ScopedValue<Long> sv17 = ScopedValue.newInstance();
        ScopedValue<Long> sv18 = ScopedValue.newInstance();
        ScopedValue<Long> sv19 = ScopedValue.newInstance();
        ScopedValue<Long> sv20 = ScopedValue.newInstance();
        ScopedValue<Long> sv21 = ScopedValue.newInstance();
        ScopedValue<Long> sv22 = ScopedValue.newInstance();
        ScopedValue<Long> sv23 = ScopedValue.newInstance();
        ScopedValue<Long> sv24 = ScopedValue.newInstance();
        ScopedValue<Long> sv25 = ScopedValue.newInstance();
        ScopedValue<Long> sv26 = ScopedValue.newInstance();
        ScopedValue<Long> sv27 = ScopedValue.newInstance();
        ScopedValue<Long> sv28 = ScopedValue.newInstance();
        ScopedValue<Long> sv29 = ScopedValue.newInstance();
        ScopedValue<Long> sv30 = ScopedValue.newInstance();
        ScopedValue<Long> sv31 = ScopedValue.newInstance();
        ScopedValue<Long> sv32 = ScopedValue.newInstance();
        ScopedValue<Long> sv33 = ScopedValue.newInstance();
        ScopedValue<Long> sv34 = ScopedValue.newInstance();
        ScopedValue<Long> sv35 = ScopedValue.newInstance();
        ScopedValue<Long> sv36 = ScopedValue.newInstance();
        ScopedValue<Long> sv37 = ScopedValue.newInstance();
        ScopedValue<Long> sv38 = ScopedValue.newInstance();
        ScopedValue<Long> sv39 = ScopedValue.newInstance();
        ScopedValue<Long> sv40 = ScopedValue.newInstance();
        ScopedValue<Long> sv41 = ScopedValue.newInstance();
        ScopedValue<Long> sv42 = ScopedValue.newInstance();
        ScopedValue<Long> sv43 = ScopedValue.newInstance();
        ScopedValue<Long> sv44 = ScopedValue.newInstance();
        ScopedValue<Long> sv45 = ScopedValue.newInstance();
        ScopedValue<Long> sv46 = ScopedValue.newInstance();
        ScopedValue<Long> sv47 = ScopedValue.newInstance();
        ScopedValue<Long> sv48 = ScopedValue.newInstance();
        ScopedValue<Long> sv49 = ScopedValue.newInstance();
        ScopedValue<Long> sv50 = ScopedValue.newInstance();
        ScopedValue<Long> sv51 = ScopedValue.newInstance();
        ScopedValue<Long> sv52 = ScopedValue.newInstance();
        ScopedValue<Long> sv53 = ScopedValue.newInstance();
        ScopedValue<Long> sv54 = ScopedValue.newInstance();
        ScopedValue<Long> sv55 = ScopedValue.newInstance();
        ScopedValue<Long> sv56 = ScopedValue.newInstance();
        ScopedValue<Long> sv57 = ScopedValue.newInstance();
        ScopedValue<Long> sv58 = ScopedValue.newInstance();
        ScopedValue<Long> sv59 = ScopedValue.newInstance();
        ScopedValue<Long> sv60 = ScopedValue.newInstance();
        ScopedValue<Long> sv61 = ScopedValue.newInstance();
        ScopedValue<Long> sv62 = ScopedValue.newInstance();
        ScopedValue<Long> sv63 = ScopedValue.newInstance();
        ScopedValue<Long> sv64 = ScopedValue.newInstance();
        ScopedValue<Long> sv65 = ScopedValue.newInstance();
        ScopedValue<Long> sv66 = ScopedValue.newInstance();
        ScopedValue<Long> sv67 = ScopedValue.newInstance();
        ScopedValue<Long> sv68 = ScopedValue.newInstance();
        ScopedValue<Long> sv69 = ScopedValue.newInstance();
        ScopedValue<Long> sv70 = ScopedValue.newInstance();
        ScopedValue<Long> sv71 = ScopedValue.newInstance();
        ScopedValue<Long> sv72 = ScopedValue.newInstance();
        ScopedValue<Long> sv73 = ScopedValue.newInstance();
        ScopedValue<Long> sv74 = ScopedValue.newInstance();
        ScopedValue<Long> sv75 = ScopedValue.newInstance();
        ScopedValue<Long> sv76 = ScopedValue.newInstance();
        ScopedValue<Long> sv77 = ScopedValue.newInstance();
        ScopedValue<Long> sv78 = ScopedValue.newInstance();
        ScopedValue<Long> sv79 = ScopedValue.newInstance();
        ScopedValue<Long> sv80 = ScopedValue.newInstance();
        ScopedValue<Long> sv81 = ScopedValue.newInstance();
        ScopedValue<Long> sv82 = ScopedValue.newInstance();
        ScopedValue<Long> sv83 = ScopedValue.newInstance();
        ScopedValue<Long> sv84 = ScopedValue.newInstance();
        ScopedValue<Long> sv85 = ScopedValue.newInstance();
        ScopedValue<Long> sv86 = ScopedValue.newInstance();
        ScopedValue<Long> sv87 = ScopedValue.newInstance();
        ScopedValue<Long> sv88 = ScopedValue.newInstance();
        ScopedValue<Long> sv89 = ScopedValue.newInstance();
        ScopedValue<Long> sv90 = ScopedValue.newInstance();
        ScopedValue<Long> sv91 = ScopedValue.newInstance();
        ScopedValue<Long> sv92 = ScopedValue.newInstance();
        ScopedValue<Long> sv93 = ScopedValue.newInstance();
        ScopedValue<Long> sv94 = ScopedValue.newInstance();
        ScopedValue<Long> sv95 = ScopedValue.newInstance();
        ScopedValue<Long> sv96 = ScopedValue.newInstance();
        ScopedValue<Long> sv97 = ScopedValue.newInstance();
        ScopedValue<Long> sv98 = ScopedValue.newInstance();
        ScopedValue<Long> sv99 = ScopedValue.newInstance();
        ScopedValue<String> MISSING = ScopedValue.newInstance();

        ScopedValue.where(sv0, 0L).run(() -> {
ScopedValue.where(sv1, 1L).run(() -> {
ScopedValue.where(sv2, 2L).run(() -> {
ScopedValue.where(sv3, 3L).run(() -> {
ScopedValue.where(sv4, 4L).run(() -> {
ScopedValue.where(sv5, 5L).run(() -> {
ScopedValue.where(sv6, 6L).run(() -> {
ScopedValue.where(sv7, 7L).run(() -> {
ScopedValue.where(sv8, 8L).run(() -> {
ScopedValue.where(sv9, 9L).run(() -> {
ScopedValue.where(sv10, 10L).run(() -> {
ScopedValue.where(sv11, 11L).run(() -> {
ScopedValue.where(sv12, 12L).run(() -> {
ScopedValue.where(sv13, 13L).run(() -> {
ScopedValue.where(sv14, 14L).run(() -> {
ScopedValue.where(sv15, 15L).run(() -> {
ScopedValue.where(sv16, 16L).run(() -> {
ScopedValue.where(sv17, 17L).run(() -> {
ScopedValue.where(sv18, 18L).run(() -> {
ScopedValue.where(sv19, 19L).run(() -> {
ScopedValue.where(sv20, 20L).run(() -> {
ScopedValue.where(sv21, 21L).run(() -> {
ScopedValue.where(sv22, 22L).run(() -> {
ScopedValue.where(sv23, 23L).run(() -> {
ScopedValue.where(sv24, 24L).run(() -> {
ScopedValue.where(sv25, 25L).run(() -> {
ScopedValue.where(sv26, 26L).run(() -> {
ScopedValue.where(sv27, 27L).run(() -> {
ScopedValue.where(sv28, 28L).run(() -> {
ScopedValue.where(sv29, 29L).run(() -> {
ScopedValue.where(sv30, 30L).run(() -> {
ScopedValue.where(sv31, 31L).run(() -> {
ScopedValue.where(sv32, 32L).run(() -> {
ScopedValue.where(sv33, 33L).run(() -> {
ScopedValue.where(sv34, 34L).run(() -> {
ScopedValue.where(sv35, 35L).run(() -> {
ScopedValue.where(sv36, 36L).run(() -> {
ScopedValue.where(sv37, 37L).run(() -> {
ScopedValue.where(sv38, 38L).run(() -> {
ScopedValue.where(sv39, 39L).run(() -> {
ScopedValue.where(sv40, 40L).run(() -> {
ScopedValue.where(sv41, 41L).run(() -> {
ScopedValue.where(sv42, 42L).run(() -> {
ScopedValue.where(sv43, 43L).run(() -> {
ScopedValue.where(sv44, 44L).run(() -> {
ScopedValue.where(sv45, 45L).run(() -> {
ScopedValue.where(sv46, 46L).run(() -> {
ScopedValue.where(sv47, 47L).run(() -> {
ScopedValue.where(sv48, 48L).run(() -> {
ScopedValue.where(sv49, 49L).run(() -> {
ScopedValue.where(sv50, 50L).run(() -> {
ScopedValue.where(sv51, 51L).run(() -> {
ScopedValue.where(sv52, 52L).run(() -> {
ScopedValue.where(sv53, 53L).run(() -> {
ScopedValue.where(sv54, 54L).run(() -> {
ScopedValue.where(sv55, 55L).run(() -> {
ScopedValue.where(sv56, 56L).run(() -> {
ScopedValue.where(sv57, 57L).run(() -> {
ScopedValue.where(sv58, 58L).run(() -> {
ScopedValue.where(sv59, 59L).run(() -> {
ScopedValue.where(sv60, 60L).run(() -> {
ScopedValue.where(sv61, 61L).run(() -> {
ScopedValue.where(sv62, 62L).run(() -> {
ScopedValue.where(sv63, 63L).run(() -> {
ScopedValue.where(sv64, 64L).run(() -> {
ScopedValue.where(sv65, 65L).run(() -> {
ScopedValue.where(sv66, 66L).run(() -> {
ScopedValue.where(sv67, 67L).run(() -> {
ScopedValue.where(sv68, 68L).run(() -> {
ScopedValue.where(sv69, 69L).run(() -> {
ScopedValue.where(sv70, 70L).run(() -> {
ScopedValue.where(sv71, 71L).run(() -> {
ScopedValue.where(sv72, 72L).run(() -> {
ScopedValue.where(sv73, 73L).run(() -> {
ScopedValue.where(sv74, 74L).run(() -> {
ScopedValue.where(sv75, 75L).run(() -> {
ScopedValue.where(sv76, 76L).run(() -> {
ScopedValue.where(sv77, 77L).run(() -> {
ScopedValue.where(sv78, 78L).run(() -> {
ScopedValue.where(sv79, 79L).run(() -> {
ScopedValue.where(sv80, 80L).run(() -> {
ScopedValue.where(sv81, 81L).run(() -> {
ScopedValue.where(sv82, 82L).run(() -> {
ScopedValue.where(sv83, 83L).run(() -> {
ScopedValue.where(sv84, 84L).run(() -> {
ScopedValue.where(sv85, 85L).run(() -> {
ScopedValue.where(sv86, 86L).run(() -> {
ScopedValue.where(sv87, 87L).run(() -> {
ScopedValue.where(sv88, 88L).run(() -> {
ScopedValue.where(sv89, 89L).run(() -> {
ScopedValue.where(sv90, 90L).run(() -> {
ScopedValue.where(sv91, 91L).run(() -> {
ScopedValue.where(sv92, 92L).run(() -> {
ScopedValue.where(sv93, 93L).run(() -> {
ScopedValue.where(sv94, 94L).run(() -> {
ScopedValue.where(sv95, 95L).run(() -> {
ScopedValue.where(sv96, 96L).run(() -> {
ScopedValue.where(sv97, 97L).run(() -> {
ScopedValue.where(sv98, 98L).run(() -> {
ScopedValue.where(sv99, 99L).run(() -> {
        for (int i = 0; i < 100_000; i++) {
            bh.consume(MISSING.orElse("hello world"));
        }
            });});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});
    }

        @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void ScopedValuesAreSlow2(Blackhole bh) {
        ScopedValue<Long> sv0 = ScopedValue.newInstance();
        ScopedValue<Long> sv1 = ScopedValue.newInstance();
        ScopedValue<Long> sv2 = ScopedValue.newInstance();
        ScopedValue<Long> sv3 = ScopedValue.newInstance();
        ScopedValue<Long> sv4 = ScopedValue.newInstance();
        ScopedValue<Long> sv5 = ScopedValue.newInstance();
        ScopedValue<Long> sv6 = ScopedValue.newInstance();
        ScopedValue<Long> sv7 = ScopedValue.newInstance();
        ScopedValue<Long> sv8 = ScopedValue.newInstance();
        ScopedValue<Long> sv9 = ScopedValue.newInstance();
        ScopedValue<Long> sv10 = ScopedValue.newInstance();
        ScopedValue<Long> sv11 = ScopedValue.newInstance();
        ScopedValue<Long> sv12 = ScopedValue.newInstance();
        ScopedValue<Long> sv13 = ScopedValue.newInstance();
        ScopedValue<Long> sv14 = ScopedValue.newInstance();
        ScopedValue<Long> sv15 = ScopedValue.newInstance();
        ScopedValue<Long> sv16 = ScopedValue.newInstance();
        ScopedValue<Long> sv17 = ScopedValue.newInstance();
        ScopedValue<Long> sv18 = ScopedValue.newInstance();
        ScopedValue<Long> sv19 = ScopedValue.newInstance();
        ScopedValue<Long> sv20 = ScopedValue.newInstance();
        ScopedValue<Long> sv21 = ScopedValue.newInstance();
        ScopedValue<Long> sv22 = ScopedValue.newInstance();
        ScopedValue<Long> sv23 = ScopedValue.newInstance();
        ScopedValue<Long> sv24 = ScopedValue.newInstance();
        ScopedValue<Long> sv25 = ScopedValue.newInstance();
        ScopedValue<Long> sv26 = ScopedValue.newInstance();
        ScopedValue<Long> sv27 = ScopedValue.newInstance();
        ScopedValue<Long> sv28 = ScopedValue.newInstance();
        ScopedValue<Long> sv29 = ScopedValue.newInstance();
        ScopedValue<Long> sv30 = ScopedValue.newInstance();
        ScopedValue<Long> sv31 = ScopedValue.newInstance();
        ScopedValue<Long> sv32 = ScopedValue.newInstance();
        ScopedValue<Long> sv33 = ScopedValue.newInstance();
        ScopedValue<Long> sv34 = ScopedValue.newInstance();
        ScopedValue<Long> sv35 = ScopedValue.newInstance();
        ScopedValue<Long> sv36 = ScopedValue.newInstance();
        ScopedValue<Long> sv37 = ScopedValue.newInstance();
        ScopedValue<Long> sv38 = ScopedValue.newInstance();
        ScopedValue<Long> sv39 = ScopedValue.newInstance();
        ScopedValue<Long> sv40 = ScopedValue.newInstance();
        ScopedValue<Long> sv41 = ScopedValue.newInstance();
        ScopedValue<Long> sv42 = ScopedValue.newInstance();
        ScopedValue<Long> sv43 = ScopedValue.newInstance();
        ScopedValue<Long> sv44 = ScopedValue.newInstance();
        ScopedValue<Long> sv45 = ScopedValue.newInstance();
        ScopedValue<Long> sv46 = ScopedValue.newInstance();
        ScopedValue<Long> sv47 = ScopedValue.newInstance();
        ScopedValue<Long> sv48 = ScopedValue.newInstance();
        ScopedValue<Long> sv49 = ScopedValue.newInstance();
        ScopedValue<Long> sv50 = ScopedValue.newInstance();
        ScopedValue<Long> sv51 = ScopedValue.newInstance();
        ScopedValue<Long> sv52 = ScopedValue.newInstance();
        ScopedValue<Long> sv53 = ScopedValue.newInstance();
        ScopedValue<Long> sv54 = ScopedValue.newInstance();
        ScopedValue<Long> sv55 = ScopedValue.newInstance();
        ScopedValue<Long> sv56 = ScopedValue.newInstance();
        ScopedValue<Long> sv57 = ScopedValue.newInstance();
        ScopedValue<Long> sv58 = ScopedValue.newInstance();
        ScopedValue<Long> sv59 = ScopedValue.newInstance();
        ScopedValue<Long> sv60 = ScopedValue.newInstance();
        ScopedValue<Long> sv61 = ScopedValue.newInstance();
        ScopedValue<Long> sv62 = ScopedValue.newInstance();
        ScopedValue<Long> sv63 = ScopedValue.newInstance();
        ScopedValue<Long> sv64 = ScopedValue.newInstance();
        ScopedValue<Long> sv65 = ScopedValue.newInstance();
        ScopedValue<Long> sv66 = ScopedValue.newInstance();
        ScopedValue<Long> sv67 = ScopedValue.newInstance();
        ScopedValue<Long> sv68 = ScopedValue.newInstance();
        ScopedValue<Long> sv69 = ScopedValue.newInstance();
        ScopedValue<Long> sv70 = ScopedValue.newInstance();
        ScopedValue<Long> sv71 = ScopedValue.newInstance();
        ScopedValue<Long> sv72 = ScopedValue.newInstance();
        ScopedValue<Long> sv73 = ScopedValue.newInstance();
        ScopedValue<Long> sv74 = ScopedValue.newInstance();
        ScopedValue<Long> sv75 = ScopedValue.newInstance();
        ScopedValue<Long> sv76 = ScopedValue.newInstance();
        ScopedValue<Long> sv77 = ScopedValue.newInstance();
        ScopedValue<Long> sv78 = ScopedValue.newInstance();
        ScopedValue<Long> sv79 = ScopedValue.newInstance();
        ScopedValue<Long> sv80 = ScopedValue.newInstance();
        ScopedValue<Long> sv81 = ScopedValue.newInstance();
        ScopedValue<Long> sv82 = ScopedValue.newInstance();
        ScopedValue<Long> sv83 = ScopedValue.newInstance();
        ScopedValue<Long> sv84 = ScopedValue.newInstance();
        ScopedValue<Long> sv85 = ScopedValue.newInstance();
        ScopedValue<Long> sv86 = ScopedValue.newInstance();
        ScopedValue<Long> sv87 = ScopedValue.newInstance();
        ScopedValue<Long> sv88 = ScopedValue.newInstance();
        ScopedValue<Long> sv89 = ScopedValue.newInstance();
        ScopedValue<Long> sv90 = ScopedValue.newInstance();
        ScopedValue<Long> sv91 = ScopedValue.newInstance();
        ScopedValue<Long> sv92 = ScopedValue.newInstance();
        ScopedValue<Long> sv93 = ScopedValue.newInstance();
        ScopedValue<Long> sv94 = ScopedValue.newInstance();
        ScopedValue<Long> sv95 = ScopedValue.newInstance();
        ScopedValue<Long> sv96 = ScopedValue.newInstance();
        ScopedValue<Long> sv97 = ScopedValue.newInstance();
        ScopedValue<Long> sv98 = ScopedValue.newInstance();
        ScopedValue<Long> sv99 = ScopedValue.newInstance();
        ScopedValue<String> MISSING = ScopedValue.newInstance();

        ScopedValue.where(sv0, 0L).run(() -> {
ScopedValue.where(sv1, 1L).run(() -> {
ScopedValue.where(sv2, 2L).run(() -> {
ScopedValue.where(sv3, 3L).run(() -> {
ScopedValue.where(sv4, 4L).run(() -> {
ScopedValue.where(sv5, 5L).run(() -> {
ScopedValue.where(sv6, 6L).run(() -> {
ScopedValue.where(sv7, 7L).run(() -> {
ScopedValue.where(sv8, 8L).run(() -> {
ScopedValue.where(sv9, 9L).run(() -> {
ScopedValue.where(sv10, 10L).run(() -> {
ScopedValue.where(sv11, 11L).run(() -> {
ScopedValue.where(sv12, 12L).run(() -> {
ScopedValue.where(sv13, 13L).run(() -> {
ScopedValue.where(sv14, 14L).run(() -> {
ScopedValue.where(sv15, 15L).run(() -> {
ScopedValue.where(sv16, 16L).run(() -> {
ScopedValue.where(sv17, 17L).run(() -> {
ScopedValue.where(sv18, 18L).run(() -> {
ScopedValue.where(sv19, 19L).run(() -> {
ScopedValue.where(sv20, 20L).run(() -> {
ScopedValue.where(sv21, 21L).run(() -> {
ScopedValue.where(sv22, 22L).run(() -> {
ScopedValue.where(sv23, 23L).run(() -> {
ScopedValue.where(sv24, 24L).run(() -> {
ScopedValue.where(sv25, 25L).run(() -> {
ScopedValue.where(sv26, 26L).run(() -> {
ScopedValue.where(sv27, 27L).run(() -> {
ScopedValue.where(sv28, 28L).run(() -> {
ScopedValue.where(sv29, 29L).run(() -> {
ScopedValue.where(sv30, 30L).run(() -> {
ScopedValue.where(sv31, 31L).run(() -> {
ScopedValue.where(sv32, 32L).run(() -> {
ScopedValue.where(sv33, 33L).run(() -> {
ScopedValue.where(sv34, 34L).run(() -> {
ScopedValue.where(sv35, 35L).run(() -> {
ScopedValue.where(sv36, 36L).run(() -> {
ScopedValue.where(sv37, 37L).run(() -> {
ScopedValue.where(sv38, 38L).run(() -> {
ScopedValue.where(sv39, 39L).run(() -> {
ScopedValue.where(sv40, 40L).run(() -> {
ScopedValue.where(sv41, 41L).run(() -> {
ScopedValue.where(sv42, 42L).run(() -> {
ScopedValue.where(sv43, 43L).run(() -> {
ScopedValue.where(sv44, 44L).run(() -> {
ScopedValue.where(sv45, 45L).run(() -> {
ScopedValue.where(sv46, 46L).run(() -> {
ScopedValue.where(sv47, 47L).run(() -> {
ScopedValue.where(sv48, 48L).run(() -> {
ScopedValue.where(sv49, 49L).run(() -> {
ScopedValue.where(sv50, 50L).run(() -> {
ScopedValue.where(sv51, 51L).run(() -> {
ScopedValue.where(sv52, 52L).run(() -> {
ScopedValue.where(sv53, 53L).run(() -> {
ScopedValue.where(sv54, 54L).run(() -> {
ScopedValue.where(sv55, 55L).run(() -> {
ScopedValue.where(sv56, 56L).run(() -> {
ScopedValue.where(sv57, 57L).run(() -> {
ScopedValue.where(sv58, 58L).run(() -> {
ScopedValue.where(sv59, 59L).run(() -> {
ScopedValue.where(sv60, 60L).run(() -> {
ScopedValue.where(sv61, 61L).run(() -> {
ScopedValue.where(sv62, 62L).run(() -> {
ScopedValue.where(sv63, 63L).run(() -> {
ScopedValue.where(sv64, 64L).run(() -> {
ScopedValue.where(sv65, 65L).run(() -> {
ScopedValue.where(sv66, 66L).run(() -> {
ScopedValue.where(sv67, 67L).run(() -> {
ScopedValue.where(sv68, 68L).run(() -> {
ScopedValue.where(sv69, 69L).run(() -> {
ScopedValue.where(sv70, 70L).run(() -> {
ScopedValue.where(sv71, 71L).run(() -> {
ScopedValue.where(sv72, 72L).run(() -> {
ScopedValue.where(sv73, 73L).run(() -> {
ScopedValue.where(sv74, 74L).run(() -> {
ScopedValue.where(sv75, 75L).run(() -> {
ScopedValue.where(sv76, 76L).run(() -> {
ScopedValue.where(sv77, 77L).run(() -> {
ScopedValue.where(sv78, 78L).run(() -> {
ScopedValue.where(sv79, 79L).run(() -> {
ScopedValue.where(sv80, 80L).run(() -> {
ScopedValue.where(sv81, 81L).run(() -> {
ScopedValue.where(sv82, 82L).run(() -> {
ScopedValue.where(sv83, 83L).run(() -> {
ScopedValue.where(sv84, 84L).run(() -> {
ScopedValue.where(sv85, 85L).run(() -> {
ScopedValue.where(sv86, 86L).run(() -> {
ScopedValue.where(sv87, 87L).run(() -> {
ScopedValue.where(sv88, 88L).run(() -> {
ScopedValue.where(sv89, 89L).run(() -> {
ScopedValue.where(sv90, 90L).run(() -> {
ScopedValue.where(sv91, 91L).run(() -> {
ScopedValue.where(sv92, 92L).run(() -> {
ScopedValue.where(sv93, 93L).run(() -> {
ScopedValue.where(sv94, 94L).run(() -> {
ScopedValue.where(sv95, 95L).run(() -> {
ScopedValue.where(sv96, 96L).run(() -> {
ScopedValue.where(sv97, 97L).run(() -> {
ScopedValue.where(sv98, 98L).run(() -> {
ScopedValue.where(sv99, 99L).run(() -> {
    ScopedValue.where(MISSING, "hello world").run(() -> {
        for (int i = 0; i < 100_000; i++) {
            bh.consume(MISSING.orElse("hello world"));
        }
    });
            });});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});});
    }
}

