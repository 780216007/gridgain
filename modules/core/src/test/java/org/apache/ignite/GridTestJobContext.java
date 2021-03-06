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

package org.apache.ignite;

import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.compute.ComputeJobContext;
import org.apache.ignite.lang.IgniteUuid;
import org.jetbrains.annotations.Nullable;

/**
 * Test job context.
 */
public class GridTestJobContext implements ComputeJobContext {
    /** */
    private final IgniteUuid jobId;

    /** */
    private final Map<Object, Object> attrs = new HashMap<>();

    /** */
    public GridTestJobContext() {
        jobId = IgniteUuid.randomUuid();
    }

    /**
     * @param jobId Job ID.
     */
    public GridTestJobContext(IgniteUuid jobId) {
        this.jobId = jobId;
    }

    /** {@inheritDoc} */
    @Override public IgniteUuid getJobId() {
        return jobId;
    }

    /** {@inheritDoc} */
    @Override public void setAttribute(Object key, @Nullable Object val) {
        attrs.put(key, val);
    }

    /** {@inheritDoc} */
    @Override public void setAttributes(Map<?, ?> attrs) {
        this.attrs.putAll(attrs);
    }

    /** {@inheritDoc} */
    @Override public <K, V> V getAttribute(K key) {
        return (V)attrs.get(key);
    }

    /** {@inheritDoc} */
    @Override public Map<Object, Object> getAttributes() {
        return new HashMap<>(attrs);
    }

    /** {@inheritDoc} */
    @Override public boolean heldcc() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public <T> T holdcc() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public <T> T holdcc(long timeout) {
        return null;
    }

    /** {@inheritDoc} */
    @Override public void callcc() {
        // No-op.
    }
}