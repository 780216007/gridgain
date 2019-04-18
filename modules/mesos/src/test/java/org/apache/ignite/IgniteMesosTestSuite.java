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

import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;
import org.apache.ignite.mesos.IgniteSchedulerSelfTest;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * Apache Mesos integration tests.
 */
@RunWith(AllTests.class)
public class IgniteMesosTestSuite {
    /**
     * @return Test suite.
     */
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("Apache Mesos Integration Test Suite");

        suite.addTest(new JUnit4TestAdapter(IgniteSchedulerSelfTest.class));

        return suite;
    }
}
