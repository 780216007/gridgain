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
package org.apache.ignite.internal.processors.cache.index;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree;
import org.apache.ignite.internal.processors.cache.persistence.tree.CorruptedTreeException;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.PageIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.util.PageHandler;
import org.apache.ignite.internal.processors.query.h2.database.H2Tree;
import org.apache.ignite.internal.stat.IoStatisticsHolder;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.testframework.ListeningTestLogger;
import org.apache.ignite.testframework.LogListener;
import org.apache.ignite.testframework.MessageOrderLogListener;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/** */
public class H2TreeCorruptedTreeExceptionTest extends GridCommonAbstractTest {
    /** */
    private static final String IDX_NAME = "A_IDX";

    /** */
    private static final String GRP_NAME = "cacheGrp";

    /** */
    private final AtomicBoolean failWithCorruptTree = new AtomicBoolean(false);

    /** */
    private final LogListener logListener = new MessageOrderLogListener(
        String.format(
                ".*?Tree is corrupted.*?cacheId=65, cacheName=A, indexName=%s, groupName=%s" +
                    ", msg=Runtime failure on row: Row@.*?key: 11, val: .*",
                IDX_NAME,
                GRP_NAME
        )
    );

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setConsistentId(gridName);

        cfg.setCacheConfiguration(new CacheConfiguration<>()
            .setName(DEFAULT_CACHE_NAME)
            .setAffinity(new RendezvousAffinityFunction().setPartitions(1))
            .setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL)
        );

        ListeningTestLogger listeningTestLog = new ListeningTestLogger(false, log);

        listeningTestLog.registerListener(logListener);

        cfg.setGridLogger(listeningTestLog);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        stopAllGrids();

        cleanPersistenceDir();

        BPlusTree.pageHndWrapper = (tree, hnd) -> {
            if (hnd instanceof BPlusTree.Insert) {
                PageHandler<Object, BPlusTree.Result> delegate = (PageHandler<Object, BPlusTree.Result>)hnd;

                return new PageHandler<Object, BPlusTree.Result>() {
                    @Override public BPlusTree.Result run(
                        int cacheId,
                        long pageId,
                        long page,
                        long pageAddr,
                        PageIO io,
                        Boolean walPlc,
                        Object arg,
                        int intArg,
                        IoStatisticsHolder statHolder
                    ) throws IgniteCheckedException {
                        BPlusTree.Result res =
                            delegate.run(cacheId, pageId, page, pageAddr, io, walPlc, arg, intArg, statHolder);

                        if (failWithCorruptTree.get() && tree instanceof H2Tree && tree.getName().contains(IDX_NAME))
                            throw new RuntimeException("test exception");

                        return res;
                    }
                };
            }
            else
                return hnd;
        };
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();

        super.afterTest();
    }

    /** */
    @Test
    public void testCorruptedTree() throws Exception {
        IgniteEx srv = startGrid(0);

        srv.cluster().active(true);

        IgniteCache<Integer, Integer> cache = srv.getOrCreateCache(DEFAULT_CACHE_NAME);

        cache.query(new SqlFieldsQuery("create table a (id integer primary key, a integer) with \"CACHE_GROUP=" + GRP_NAME + "\""));
        cache.query(new SqlFieldsQuery("create index " + IDX_NAME + " on a(a)"));

        failWithCorruptTree.set(true);

        try {
            cache.query(new SqlFieldsQuery("insert into a(id, a) values (11, 1)"));

            fail("Cache operations are expected to fail");
        }
        catch (Throwable e) {
            assertTrue(X.hasCause(e, CorruptedTreeException.class));
        }

        assertTrue(logListener.check());
    }
}
