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

import java.util.List;
import java.util.concurrent.Callable;
import org.apache.ignite.IgniteException;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.transactions.Transaction;
import org.junit.Test;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.apache.ignite.testframework.GridTestUtils.assertThrows;
import static org.apache.ignite.testframework.GridTestUtils.getFieldValue;

/**
 * Tests that verify the execution of GridCacheProcessor operations
 * in active transactions.
 */
public class GridCacheProcessorActiveTxTest extends GridCommonAbstractTest {
    /**
     * The prefix for the exception message.
     */
    private static final String CHECK_EMPTY_TRANSACTIONS_ERROR_MSG =
        "Cannot start/stop cache within lock or transaction.";

    /**
     * Format for displaying the cache name and operation.
     *
     * @see GridCacheProcessor#CACHE_NAME_AND_OPERATION_FORMAT
     */
    private static final String CACHE_NAME_AND_OPERATION_FORMAT =
        getFieldValue(GridCacheProcessor.class, "CACHE_NAME_AND_OPERATION_FORMAT");

    /**
     * Format for displaying the cache names and operation.
     *
     * @see GridCacheProcessor#CACHE_NAMES_AND_OPERATION_FORMAT
     */
    private static final String CACHE_NAMES_AND_OPERATION_FORMAT =
        getFieldValue(GridCacheProcessor.class, "CACHE_NAMES_AND_OPERATION_FORMAT");

    /** Node. */
    private static IgniteEx NODE;

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        NODE = startGrid(0);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        NODE.destroyCaches(NODE.cacheNames());

        super.afterTest();
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();

        super.afterTestsStopped();
    }

    /**
     * Checking the throw exception during the operation "dynamicStartCache".
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicSingleCacheStart() throws Exception {
        String cacheName = DEFAULT_CACHE_NAME;

        opInActiveTx(
            () -> NODE.createCache(new CacheConfiguration<>(cacheName)),
            CACHE_NAME_AND_OPERATION_FORMAT,
            cacheName,
            "dynamicStartCache"
        );
    }

    /**
     * Checking the throw exception during the operation
     * "dynamicStartCachesByStoredConf".
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicStartMultipleCaches() throws Exception {
        List<String> cacheNames = cacheNames();

        List<CacheConfiguration> cacheCfgs = cacheConfigurations(cacheNames);

        opInActiveTx(
            () -> NODE.createCaches(cacheCfgs),
            CACHE_NAMES_AND_OPERATION_FORMAT,
            cacheNames.toString(),
            "dynamicStartCachesByStoredConf"
        );
    }

    /**
     * Checking the throw exception during the operation "dynamicDestroyCache".
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicCacheDestroy() throws Exception {
        String cacheName = DEFAULT_CACHE_NAME;

        NODE.createCache(new CacheConfiguration<>(cacheName));

        opInActiveTx(
            () -> {
                NODE.destroyCache(cacheName);
                return null;
            },
            CACHE_NAME_AND_OPERATION_FORMAT,
            cacheName,
            "dynamicDestroyCache"
        );
    }

    /**
     * Checking the throw exception during the operation
     * "dynamicDestroyCaches".
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicDestroyMultipleCaches() throws Exception {
        List<String> cacheNames = cacheNames();

        List<CacheConfiguration> cacheCfgs = cacheConfigurations(cacheNames);

        NODE.createCaches(cacheCfgs);

        opInActiveTx(
            () -> {
                NODE.destroyCaches(cacheNames);
                return null;
            },
            CACHE_NAMES_AND_OPERATION_FORMAT,
            cacheNames.toString(),
            "dynamicDestroyCaches"
        );
    }

    /**
     * Checking the throw exception during the operation "dynamicCloseCache".
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDynamicCacheClose() throws Exception {
        GridCacheProcessor cacheProcessor = NODE.context().cache();

        String cacheName = DEFAULT_CACHE_NAME;

        NODE.getOrCreateCache(new CacheConfiguration<>(cacheName));

        opInActiveTx(
            () -> cacheProcessor.dynamicCloseCache(cacheName),
            CACHE_NAME_AND_OPERATION_FORMAT,
            cacheName,
            "dynamicCloseCache"
        );

        NODE.destroyCache(cacheName);
    }

    /**
     * Checking the throw exception during the operation "resetCacheState".
     *
     * @throws Exception If failed.
     */
    @Test
    public void testResetCacheState() throws Exception {
        List<String> cacheNames = cacheNames();

        opInActiveTx(
            () -> NODE.context().cache().resetCacheState(cacheNames),
            CACHE_NAME_AND_OPERATION_FORMAT,
            cacheNames.toString(),
            "resetCacheState"
        );
    }

    /**
     * Create cache names.
     *
     * @return Cache names.
     */
    private List<String> cacheNames() {
        return range(0, 2).mapToObj(i -> DEFAULT_CACHE_NAME + i).collect(toList());
    }

    /**
     * Creating cache configurations in which only cache name is set.
     *
     * @param cacheNames The names of caches.
     * @return Cache configurations.
     */
    private List<CacheConfiguration> cacheConfigurations(List<String> cacheNames) {
        assert nonNull(cacheNames);

        return cacheNames.stream().map(CacheConfiguration::new).collect(toList());
    }

    /**
     * Performing an operation in an active transaction with a check that an
     * exception will be thrown with a format
     * {@link #CHECK_EMPTY_TRANSACTIONS_ERROR_MSG} +
     * {@link #CACHE_NAMES_AND_OPERATION_FORMAT} of
     * {@link #CACHE_NAME_AND_OPERATION_FORMAT} message.
     *
     * @param callable Operation in an active transaction.
     * @param format Format for cache(s) name and operation
     *      in the exception message.
     * @param cacheName Cache name for the exception message.
     * @param operation Operation for the exception message.
     * @throws Exception If failed.
     */
    private void opInActiveTx(
        Callable<?> callable,
        String format,
        String cacheName,
        String operation
    ) throws Exception {
        assert nonNull(callable);
        assert nonNull(format);
        assert nonNull(cacheName);
        assert nonNull(operation);

        try (Transaction transaction = NODE.transactions().txStart()) {
            assertThrows(
                log,
                callable,
                IgniteException.class,
                String.format(CHECK_EMPTY_TRANSACTIONS_ERROR_MSG + ' ' + format, cacheName, operation)
            );
        }

        callable.call();
    }
}
