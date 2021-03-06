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

package org.apache.ignite.internal.processors.cache;

import org.junit.Before;
import org.junit.Test;

import org.apache.ignite.testframework.MvccFeatureChecker;

/**
 * Tests peek modes with near tx cache.
 */
public class IgniteCacheTxNearPeekModesTest extends IgniteCacheTxPeekModesTest {
    /** */
    @Before
    public void beforeIgniteCacheTxNearPeekModesTest() {
        MvccFeatureChecker.skipIfNotSupported(MvccFeatureChecker.Feature.NEAR_CACHE);
    }

    /** {@inheritDoc} */
    @Override protected boolean hasNearCache() {
        return true;
    }

    /** {@inheritDoc} */
    @Test
    @Override public void testLocalPeek() throws Exception {
        // TODO: uncomment and re-open ticket if fails.
//        fail("https://issues.apache.org/jira/browse/IGNITE-1824");

        super.testLocalPeek();
    }
}
