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

package org.apache.ignite.internal.processors.cache.distributed.dht;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.cache.Cache;
import javax.cache.configuration.Factory;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.store.CacheStore;
import org.apache.ignite.cache.store.CacheStoreAdapter;
import org.apache.ignite.configuration.NearCacheConfiguration;
import org.apache.ignite.internal.processors.cache.IgniteCacheAbstractTest;
import org.apache.ignite.lang.IgniteBiInClosure;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.testframework.MvccFeatureChecker;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;

/**
 * Load cache test.
 */
@SuppressWarnings("unchecked")
public class GridCacheGlobalLoadTest extends IgniteCacheAbstractTest {
    /** */
    private static ConcurrentMap<String, Object[]> map;

    /** */
    private static volatile boolean failStore;

    /** {@inheritDoc} */
    @Override protected int gridCount() {
        return 3;
    }

    /** {@inheritDoc} */
    @Override protected CacheMode cacheMode() {
        return PARTITIONED;
    }

    /** {@inheritDoc} */
    @Override protected CacheAtomicityMode atomicityMode() {
        return TRANSACTIONAL;
    }

    /** {@inheritDoc} */
    @Override protected NearCacheConfiguration nearConfiguration() {
        return new NearCacheConfiguration();
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        MvccFeatureChecker.skipIfNotSupported(MvccFeatureChecker.Feature.CACHE_STORE);

        super.beforeTestsStarted();
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testLoadCache() throws Exception {
        loadCache(false);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testLoadCacheAsync() throws Exception {
        loadCache(true);
    }

    /**
     * @param async If {@code true} uses asynchronous method.
     */
    private void loadCache(boolean async) {
        IgniteCache<Integer, Integer> cache = jcache();

        map = new ConcurrentHashMap<>();

        if (async)
            cache.loadCacheAsync(null, 1, 2, 3).get();
        else
            cache.loadCache(null, 1, 2, 3);

        assertEquals(3, map.size());

        Object[] expArgs = {1, 2, 3};

        for (int i = 0; i < gridCount(); i++) {
            Object[] args = map.get(getTestIgniteInstanceName(i));

            Assert.assertArrayEquals(expArgs, args);
        }

        assertEquals(cache.get(1), (Integer)1);
        assertEquals(cache.get(2), (Integer)2);
        assertEquals(cache.get(3), (Integer)3);

        map = new ConcurrentHashMap<>();

        if (async) {
            cache.loadCacheAsync(new IgniteBiPredicate<Integer, Integer>() {
                @Override public boolean apply(Integer key, Integer val) {
                    assertNotNull(key);
                    assertNotNull(val);

                    return key % 2 == 0;
                }
            }, 1, 2, 3, 4, 5, 6).get();
        }
        else {
            cache.loadCache(new IgniteBiPredicate<Integer, Integer>() {
                @Override public boolean apply(Integer key, Integer val) {
                    assertNotNull(key);
                    assertNotNull(val);

                    return key % 2 == 0;
                }
            }, 1, 2, 3, 4, 5, 6);
        }

        assertEquals(3, map.size());

        expArgs = new Object[]{1, 2, 3, 4, 5, 6};

        for (int i = 0; i < gridCount(); i++) {
            Object[] args = map.get(getTestIgniteInstanceName(i));

            Assert.assertArrayEquals(expArgs, args);
        }

        assertEquals(cache.get(1), (Integer)1);
        assertEquals(cache.get(2), (Integer)2);
        assertEquals(cache.get(3), (Integer)3);
        assertEquals(cache.get(4), (Integer)4);
        assertEquals(cache.get(6), (Integer)6);
        assertNull(cache.get(5));
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        failStore = true;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        map = null;

        failStore = false;

        IgniteCache<Integer, Integer> cache = jcache();

        for (int i = 0; i < 7; i++)
            cache.remove(i);
    }

    /** {@inheritDoc} */
    @Override protected Factory<CacheStore> cacheStoreFactory() {
        return (Factory)singletonFactory(new TestStore());
    }

    /**
     * Test store.
     */
    private static class TestStore extends CacheStoreAdapter<Integer, Integer> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** {@inheritDoc} */
        @Override public void loadCache(IgniteBiInClosure<Integer, Integer> clo,
            @Nullable Object... args) {
            assertNotNull(ignite);
            assertNotNull(clo);
            assertNotNull(map);
            assertNotNull(args);

            assertNull(map.put(ignite.name(), args));

            for (Object arg : args) {
                Integer key = (Integer)arg;

                clo.apply(key, key);
            }
        }

        /** {@inheritDoc} */
        @Override public Integer load(Integer key) {
            if (failStore)
                assertEquals((Integer)5, key);

            return null;
        }

        /** {@inheritDoc} */
        @Override public void write(Cache.Entry<? extends Integer, ? extends Integer> e) {
            fail();
        }

        /** {@inheritDoc} */
        @Override public void delete(Object key) {
            if (failStore)
                fail();
        }
    }
}
