/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.metric;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Histogram metric that will calculate counts of measurements that gets into each bounds interval.
 * Note, that {@link #value()} will return array length of {@code bounds.length + 1}.
 * Last element will contains count of measurements bigger then most right value of bounds.
 */
public class HistogramMetric {
    /** Holder of measurements. */
    private volatile HistogramHolder holder;

    /** */
    private String name;

    /** */
    private String desc;

    /**
     * @param name Name.
     * @param desc Description.
     * @param bounds Bounds.
     */
    public HistogramMetric(String name, @Nullable String desc, long[] bounds) {
        this.name = name;
        this.desc = desc;
        holder = new HistogramHolder(bounds);
    }

    /**
     * Sets value.
     *
     * @param x Value.
     */
    public void value(long x) {
        assert x >= 0;

        HistogramHolder h = holder;

        //Expect arrays of few elements.
        for (int i = 0; i < h.bounds.length; i++) {
            if (x <= h.bounds[i]) {
                h.measurements.incrementAndGet(i);

                return;
            }
        }

        h.measurements.incrementAndGet(h.bounds.length);
    }

    /**
     * Resets histogram state with the specified bounds.
     *
     * @param bounds Bounds.
     */
    public void reset(long[] bounds) {
        holder = new HistogramHolder(bounds);
    }

    /**
     * Resets histogram.
     */
    public void reset() {
        reset(holder.bounds);
    }

    /**
     * @return Histogram values.
     */
    public long[] value() {
        HistogramHolder h = holder;

        long[] res = new long[h.measurements.length()];

        for (int i = 0; i < h.measurements.length(); i++)
            res[i] = h.measurements.get(i);

        return res;
    }

    /**
     * Adds all values from {@code other} to this HistogramMetric.
     * Works inplace.
     * @param other Other.
     */
    public void addValues(@NotNull HistogramMetric other) {
        HistogramHolder h = holder;

        if (Arrays.equals(h.bounds, other.holder.bounds)) {
            for (int i = 0; i < h.bounds.length; i++)
                h.measurements.set(i, h.measurements.get(i) + other.holder.measurements.get(i));
        }
    }

    /** Histogram holder. */
    private static class HistogramHolder {
        /** Count of measurement for each bound. */
        public final AtomicLongArray measurements;

        /** Bounds of measurements. */
        public final long[] bounds;

        /**
         * @param bounds Bounds of measurements.
         */
        public HistogramHolder(long[] bounds) {
            assert !F.isEmpty(bounds) && F.isSorted(bounds);

            this.bounds = bounds;

            this.measurements = new AtomicLongArray(bounds.length + 1);
        }
    }
}