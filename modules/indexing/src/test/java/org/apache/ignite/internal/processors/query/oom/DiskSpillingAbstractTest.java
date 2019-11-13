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
package org.apache.ignite.internal.processors.query.oom;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.processors.cache.query.SqlFieldsQueryEx;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing.DISK_SPILL_DIR;

/**
 * Base class for disk spilling tests.
 */
@WithSystemProperty(key = "IGNITE_SQL_USE_DISK_OFFLOAD", value = "true")
@WithSystemProperty(key = "IGNITE_SQL_MEMORY_RESERVATION_BLOCK_SIZE", value = "2048")
public class DiskSpillingAbstractTest extends GridCommonAbstractTest {
    /** */
    private static final int PERS_CNT = 1000;

    /** */
    private static final int DEPS_CNT = 100;

    /** */
    protected static final long SMALL_MEM_LIMIT = 4096;

    /** */
    private static final long HUGE_MEM_LIMIT = Long.MAX_VALUE;

    /** */
    protected boolean checkSortOrder;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        // Dummy cache.
        CacheConfiguration<?,?> cache = defaultCacheConfiguration();

        cfg.setCacheConfiguration(cache);

        if (persistence()) {
            DataStorageConfiguration storageCfg = new DataStorageConfiguration();

            DataRegionConfiguration regionCfg = new DataRegionConfiguration();
            regionCfg.setPersistenceEnabled(true);

            storageCfg.setDefaultDataRegionConfiguration(regionCfg);

            cfg.setDataStorageConfiguration(storageCfg);
        }

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        cleanPersistenceDir();

        startGrids(nodeCount());

        if (persistence())
            grid(0).cluster().active(true);

        CacheConfiguration<?,?> personCache = defaultCacheConfiguration();
        personCache.setQueryParallelism(queryParallelism());
        personCache.setName("person");
        grid(0).addCacheConfiguration(personCache);

        CacheConfiguration<?,?> orgCache = defaultCacheConfiguration();
        orgCache.setQueryParallelism(queryParallelism());
        orgCache.setName("organization");
        grid(0).addCacheConfiguration(orgCache);

        startGrid(getConfiguration("client").setClientMode(true));


        populateData();
    }

    /** */
    protected int nodeCount() {
        return 1;
    }

    /** */
    protected boolean persistence() {
        return false;
    }

    /** */
    protected int queryParallelism() {
        return 1;
    }

    /** */
    protected boolean fromClient() {
        return false;
    }

    /** */
    protected boolean localQuery() {
        return false;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        checkSortOrder = false;
    }


    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        FileUtils.cleanDirectory(getWorkDir().toFile());
    }

    /** */
    protected void assertInMemoryAndOnDiskSameResults(boolean lazy, String sql) {
        WatchService watchSvc = null;

        try {
            Path workDir = getWorkDir();

            if (log.isInfoEnabled())
                log.info("workDir=" + workDir.toString());

            watchSvc = FileSystems.getDefault().newWatchService();

            WatchKey watchKey = workDir.register(watchSvc, ENTRY_CREATE, ENTRY_DELETE);

            // In-memory.
            long startInMem = System.currentTimeMillis();

            if (log.isInfoEnabled())
                log.info("Run query in memory.");

            watchKey.reset();

            List<List<?>> inMemRes = runSql(sql, lazy, HUGE_MEM_LIMIT);

            assertFalse("In-memory result is empty.", inMemRes.isEmpty());

            assertWorkDirClean();

            List<WatchEvent<?>> dirEvts = watchKey.pollEvents();

            // No files should be created for in-memory mode.
            assertTrue("Disk events is not empty for in-memory query: :" + dirEvts.stream().map(e ->
                e.kind().toString()).collect(Collectors.joining(", ")), dirEvts.isEmpty());

            // On disk.
            if (log.isInfoEnabled())
                log.info("Run query with disk offloading.");

            long startOnDisk = System.currentTimeMillis();

            watchKey.reset();

            List<List<?>> onDiskRes = runSql(sql, lazy, SMALL_MEM_LIMIT);

            assertFalse("On disk result is empty.", onDiskRes.isEmpty());

            long finish = System.currentTimeMillis();

            dirEvts = watchKey.pollEvents();

            // Check files have been created but deleted later.
            assertFalse("Disk events is empty for on-disk query. ", dirEvts.isEmpty());

            assertWorkDirClean();

            if (log.isInfoEnabled()) {
                log.info("Spill files events (created + deleted): " + dirEvts.size());
            }

            if (!checkSortOrder) {
                fixSortOrder(onDiskRes);
                fixSortOrder(inMemRes);
            }

            if (log.isInfoEnabled())
                log.info("In-memory time=" + (startOnDisk - startInMem) + ", on-disk time=" + (finish - startOnDisk));

            if (log.isDebugEnabled())
                log.debug("In-memory result:\n" + inMemRes + "\nOn disk result:\n" + onDiskRes);

            assertEqualsCollections(inMemRes, onDiskRes);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            U.closeQuiet(watchSvc);
        }
    }

    /** */
    private void populateData() {
        // Persons
        runDdlDml("CREATE TABLE person (" +
            "id BIGINT PRIMARY KEY, " +
            "name VARCHAR, " +
            "depId SMALLINT, " +
            "code CHAR(3)," +
            "male BOOLEAN," +
            "age TINYINT," +
            "height SMALLINT," +
            "salary INT," +
            "tax DECIMAL(4,4)," +
            "weight DOUBLE," +
            "temperature REAL," +
            "time TIME," +
            "date DATE," +
            "timestamp TIMESTAMP," +
            "uuid UUID, " +
            "nulls INT) " +
            "WITH \"TEMPLATE=person\"");

        for (int i = 0; i < PERS_CNT; i++) {
            runDdlDml("INSERT INTO person (" +
                    "id, " +
                    "name, " +
                    "depId,  " +
                    "code, " +
                    "male, " +
                    "age, " +
                    "height, " +
                    "salary, " +
                    "tax, " +
                    "weight, " +
                    "temperature," +
                    "time," +
                    "date," +
                    "timestamp," +
                    "uuid, " +
                    "nulls) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                i,                    // id
                "Vasya" + i,                // name
                i % DEPS_CNT,               // depId
                "p" + i % 31,               // code
                i % 2,                      // male
                i % 100,                    // age
                150 + (i % 50),             // height
                50000 + i,                  // salary
                i / 1000d,       // tax
                50d + i % 50.0,             // weight
                36.6,                       // temperature
                "20:00:" + i % 60,          // time
                "2019-04-" + (i % 29 + 1),  // date
                "2019-04-04 04:20:08." + i % 900, // timestamp
                "736bc956-090c-40d2-94da-916f2161cda" + i % 10, // uuid
                null);                      // nulls
        }

        // Departments
        runDdlDml("CREATE TABLE department (" +
            "id INT PRIMARY KEY, " +
            "title VARCHAR_IGNORECASE) " +
            "WITH \"TEMPLATE=organization\"");

        for (int i = 0; i < DEPS_CNT; i++) {
            runDdlDml("INSERT INTO department (id, title) VALUES (?, ?)", i, "IT" + i);
        }
    }

    /** */
    private List<List<?>> runSql(String sql, boolean lazy, long memLimit) {
        Ignite node = fromClient() ? grid("client") : grid(0);
        return node.cache(DEFAULT_CACHE_NAME).query(new SqlFieldsQueryEx(sql, null)
            .setMaxMemory(memLimit)
            .setLazy(lazy)
            .setLocal(localQuery())
        ).getAll();
    }

    /** */
    private List<List<?>> runDdlDml(String sql, Object... args) {
        return grid(0)
            .cache(DEFAULT_CACHE_NAME)
            .query(new SqlFieldsQueryEx(sql, null)
                .setArgs(args)
            ).getAll();
    }

    /** */
    private void fixSortOrder(List<List<?>> res) {
        Collections.sort(res, new Comparator<List<?>>() {
            @Override public int compare(List<?> l1, List<?> l2) {
                for (int i = 0; i < l1.size(); i++) {
                    Object o1 = l1.get(i);
                    Object o2 = l2.get(i);

                    if (o1 == null  ||  o2 == null) {
                        if (o1 == null && o2 == null)
                            return 0;

                        return o1 == null ? 1 : -1;
                    }

                    if (o1.hashCode() == o2.hashCode())
                        continue;

                    return o1.hashCode() > o2.hashCode() ? 1 : -1;
                }

                return 0;
            }
        });
    }

    /** */
    protected Path getWorkDir() {
        Path workDir = Paths.get(grid(0).configuration().getWorkDirectory(), DISK_SPILL_DIR);

        workDir.toFile().mkdir();
        return workDir;
    }

    /** */
    protected void assertWorkDirClean() {
        List<String> spillFiles = listOfSpillFiles();

        assertEquals("Files are not deleted: " + spillFiles,  0, spillFiles.size());
    }

    /** */
    protected List<String> listOfSpillFiles() {
        Path workDir = getWorkDir();

        assertTrue(workDir.toFile().isDirectory());

        return Arrays.asList(workDir.toFile().list());
    }
}