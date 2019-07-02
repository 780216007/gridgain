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
package org.apache.ignite.testsuites;

import java.util.HashSet;
import junit.framework.TestSuite;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.processors.cache.persistence.IgnitePdsContinuousRestartTest;
import org.apache.ignite.internal.processors.cache.persistence.IgnitePdsContinuousRestartTestWithExpiryPolicy;

/**
 * Mvcc version of {@link IgnitePdsTestSuite3}.
 */
public class IgnitePdsMvccTestSuite3 extends TestSuite {
    /**
     * @return Suite.
     */
    public static TestSuite suite() {
        System.setProperty(IgniteSystemProperties.IGNITE_FORCE_MVCC_MODE_IN_TESTS, "true");

        TestSuite suite = new TestSuite("Ignite Persistent Store Mvcc Test Suite 3");

        HashSet<Class> ignoredTests = new HashSet<>();

        // TODO https://issues.apache.org/jira/browse/IGNITE-11937
        ignoredTests.add(IgnitePdsContinuousRestartTest.class);
        ignoredTests.add(IgnitePdsContinuousRestartTestWithExpiryPolicy.class);

        suite.addTest(IgnitePdsTestSuite3.suite(ignoredTests));

        return suite;
    }
}