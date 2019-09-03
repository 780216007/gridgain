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

package org.apache.ignite.client;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.metric.GridMetricManager;
import org.apache.ignite.internal.processors.metric.MetricRegistry;
import org.apache.ignite.spi.metric.IntMetric;
import org.apache.ignite.spi.metric.Metric;
import org.apache.ignite.testframework.GridTestUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * High Availability tests.
 */
public class MetricTest {
    /** */
    private static String METRICS_NAMESPACE_CLIENT = "client";

    /** */
    private static String METRICS_NAMESPACE_SESSIONS = METRICS_NAMESPACE_CLIENT + ".sessions";

    /** */
    private static String METRICS_NAMESPACE_SESSIONS_THIN = METRICS_NAMESPACE_SESSIONS + ".thin";

    /** */
    private static String METRICS_NAMESPACE_REQUESTS_THIN = METRICS_NAMESPACE_CLIENT + ".requests.thin";

    /** */
    private static String METRIC_SESSIONS_WAITING = METRICS_NAMESPACE_SESSIONS + ".rejectedDueTimeout";

    /** */
    private static String METRIC_SESSIONS_REJECTED_DUE_TIMEOUT = METRICS_NAMESPACE_SESSIONS + ".rejectedDueTimeout";

    /** */
    private static String METRIC_SESSIONS_REJECTED_DUE_PARSING = METRICS_NAMESPACE_SESSIONS + ".rejectedDueParsingError";

    /** */
    private static String METRIC_SESSIONS_REJECTED_DUE_HANDSHAKE = METRICS_NAMESPACE_SESSIONS_THIN + ".rejectedDueHandshakeParams";

    /** */
    private static String METRIC_SESSIONS_REJECTED_DUE_AUTH = METRICS_NAMESPACE_SESSIONS_THIN + ".rejectedDueAuthentication";

    /** */
    private static String METRIC_SESSIONS_ACCEPTED = METRICS_NAMESPACE_SESSIONS_THIN + ".accepted";

    /** */
    private static String METRIC_SESSIONS_ACTIVE = METRICS_NAMESPACE_SESSIONS_THIN + ".active";

    /** */
    private static String METRIC_SESSIONS_CLOSED = METRICS_NAMESPACE_SESSIONS_THIN + ".closed";

    /** */
    private static String METRIC_REQUESTS_HANDLED = METRICS_NAMESPACE_REQUESTS_THIN + ".handled";

    /** */
    private static String METRIC_REQUESTS_FAILED = METRICS_NAMESPACE_REQUESTS_THIN + ".failed";


    /** Per test timeout */
    @Rule
    public Timeout globalTimeout = new Timeout((int) GridTestUtils.DFLT_TEST_TIMEOUT);

    /**
     * Thin clint failover.
     */
    @Test
    public void testSessions() throws Exception {
        try (Ignite ignored = startNode()) {
            try (IgniteClient ignored1 = Ignition.startClient(getClientConfiguration())) {

                assertEquals(0, getIntMetricValue(METRIC_SESSIONS_WAITING));
                assertEquals(1, getIntMetricValue(METRIC_SESSIONS_ACCEPTED));
                assertEquals(1, getIntMetricValue(METRIC_SESSIONS_ACTIVE));
                assertEquals(0, getIntMetricValue(METRIC_SESSIONS_CLOSED));

                checkNothingRejected();
            }

            Thread.sleep(100);

            assertEquals(0, getIntMetricValue(METRIC_SESSIONS_WAITING));
            assertEquals(1, getIntMetricValue(METRIC_SESSIONS_ACCEPTED));
            assertEquals(0, getIntMetricValue(METRIC_SESSIONS_ACTIVE));
            assertEquals(1, getIntMetricValue(METRIC_SESSIONS_CLOSED));

            checkNothingRejected();

            try (IgniteClient ignored1 = Ignition.startClient(getClientConfiguration())) {

                assertEquals(0, getIntMetricValue(METRIC_SESSIONS_WAITING));
                assertEquals(2, getIntMetricValue(METRIC_SESSIONS_ACCEPTED));
                assertEquals(1, getIntMetricValue(METRIC_SESSIONS_ACTIVE));
                assertEquals(1, getIntMetricValue(METRIC_SESSIONS_CLOSED));

                checkNothingRejected();
            }

            Thread.sleep(100);

            assertEquals(0, getIntMetricValue(METRIC_SESSIONS_WAITING));
            assertEquals(2, getIntMetricValue(METRIC_SESSIONS_ACCEPTED));
            assertEquals(0, getIntMetricValue(METRIC_SESSIONS_ACTIVE));
            assertEquals(2, getIntMetricValue(METRIC_SESSIONS_CLOSED));

            checkNothingRejected();
        }
    }

    /**
     * Check that nothing was rejected.
     */
    private static void checkNothingRejected() {
        assertEquals(0, getIntMetricValue(METRIC_SESSIONS_REJECTED_DUE_TIMEOUT));
        assertEquals(0, getIntMetricValue(METRIC_SESSIONS_REJECTED_DUE_PARSING));
        assertEquals(0, getIntMetricValue(METRIC_SESSIONS_REJECTED_DUE_HANDSHAKE));
        assertEquals(0, getIntMetricValue(METRIC_SESSIONS_REJECTED_DUE_AUTH));
    }

    /**
     * Get value of int metric. Fail if not found.
     * @param metricFull Full name of metric.
     */
    private static int getIntMetricValue(String metricFull) {
        IntMetric metric = getMetric(metricFull);

        assertNotNull("Int metric was not found: " + metricFull, metric);

        return metric.value();
    }

    /**
     * Get value of metric. Fail if not found.
     * @param metricFull Full name of metric.
     */
    @Nullable private static <M extends Metric> M getMetric(String metricFull) {
        int lastDot = metricFull.lastIndexOf('.');

        String registryName = metricFull.substring(0, lastDot);
        String metricName = metricFull.substring(lastDot + 1);

        GridMetricManager manager = metricManager(Ignition.ignite());

        MetricRegistry registry = manager.registry(registryName);

        return registry.findMetric(metricName);
    }

    /** Start node. */
    private static Ignite startNode() {
        return Ignition.start(new IgniteConfiguration());
    }

    /** Get metric manager. */
    private static GridMetricManager metricManager(Ignite node) {
        return ((IgniteEx)node).context().metric();
    }

    /** Get client configuration. */
    private static ClientConfiguration getClientConfiguration() {
        return new ClientConfiguration()
            .setAddresses(Config.SERVER);
    }
}