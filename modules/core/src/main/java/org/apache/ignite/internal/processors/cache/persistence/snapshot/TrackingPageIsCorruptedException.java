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

package org.apache.ignite.internal.processors.cache.persistence.snapshot;

import org.apache.ignite.IgniteCheckedException;

/**
 * Thrown when corrupted tracking page was queried.
 */
public class TrackingPageIsCorruptedException extends IgniteCheckedException {
    /** */
    private static final long serialVersionUID = 0L;

    /** Instance. */
    public static final TrackingPageIsCorruptedException INSTANCE = new TrackingPageIsCorruptedException(-1, -1);

    /** Last tag. */
    private final long lastTag;

    /** Passed tag. */
    private final long passedTag;

    /**
     * @param lastTag Last tag.
     * @param passedTag Passed tag.
     */
    public TrackingPageIsCorruptedException(long lastTag, long passedTag) {
        this.lastTag = lastTag;
        this.passedTag = passedTag;
    }

    /**
     * @return Last tag.
     */
    public long lastTag() {
        return lastTag;
    }

    /**
     * @return Passed tag.
     */
    public long passedTag() {
        return passedTag;
    }
}
