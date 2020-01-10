/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.util;

import java.io.File;
import java.util.Collections;
import java.util.List;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.pagemem.wal.record.DataEntry;
import org.apache.ignite.internal.processors.cache.CacheObjectImpl;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheOperation;
import org.apache.ignite.internal.processors.cache.KeyCacheObjectImpl;
import org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.junit.Test;

import static java.io.File.separatorChar;
import static org.apache.ignite.internal.commandline.CommandHandler.EXIT_CODE_OK;
import static org.apache.ignite.internal.processors.diagnostic.DiagnosticProcessor.DEFAULT_TARGET_FOLDER;

/**
 * Tests for checking partition reconciliation control.sh command.
 */
public abstract class GridCommandHandlerPartitionReconciliationAbstractTest extends
    GridCommandHandlerClusterPerMethodAbstractTest {
    /** */
    public static final int INVALID_KEY = 100;

    /** */
    public static final String VALUE_PREFIX = "abc_";

    /** */
    public static final String BROKEN_POSTFIX_1 = "_broken1";

    /** */
    public static final String BROKEN_POSTFIX_2 = "_broken2";

    /** */
    protected static File dfltDiagnosticDir;

    /** */
    protected static File customDiagnosticDir;

    /** */
    protected IgniteEx ignite;

    /**
     * <ul>
     * <li>Init diagnostic and persistence dirs.</li>
     * <li>Start 4 nodes and activate cluster.</li>
     * <li>Prepare cache.</li>
     * </ul>
     *
     * @throws Exception If failed.
     */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        initDiagnosticDir();

        cleanDiagnosticDir();

        cleanPersistenceDir();

        ignite = startGrids(4);

        ignite.cluster().active(true);

        prepareCache();
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        super.afterTestsStopped();

        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        // Do nothing;
    }

    /**
     * Create cache and populate it with some data.
     */
    protected abstract void prepareCache();

    /** {@inheritDoc} */
    @Override protected void cleanPersistenceDir() throws Exception {
        super.cleanPersistenceDir();

        cleanDiagnosticDir();
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    protected void initDiagnosticDir() throws IgniteCheckedException {
        dfltDiagnosticDir = new File(U.defaultWorkDirectory()
            + separatorChar + DEFAULT_TARGET_FOLDER + separatorChar);

        customDiagnosticDir = new File(U.defaultWorkDirectory()
            + separatorChar + "diagnostic_test_dir" + separatorChar);
    }

    /**
     * Clean diagnostic directories.
     */
    protected void cleanDiagnosticDir() {
        U.delete(dfltDiagnosticDir);
        U.delete(customDiagnosticDir);
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode.</li>
     * <p>
     * <b>Expected result:</b>
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * (MAJORITY in given case) and also contains the value that was used for fix (val1 in given case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes has same value that is equal to val1
     * in given case.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testRemovedEntryOnPrimaryWithDefaultRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(true);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode",
            "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>MAJORITY</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * (MAJORITY in given case) and also contains the value that was used for fix (val1 in given case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes has same value that is equal to val1
     * in given case.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testRemovedEntryOnPrimaryWithMajorityRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(true);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "MAJORITY", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>PRIMARY</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * (PRIMARY in given case) and also contains the value that was used for fix (null in given case).</li>
     * <li>Ensure that previously inconsistent key was removed on all 4 nodes.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testRemovedEntryOnPrimaryWithPrimaryRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(true);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "PRIMARY", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertNull(val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>MAX_GRID_CACHE_VERSION</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * (MAX_GRID_CACHE_VERSION in given case) and also contains the value that was used for fix (val3 in given
     * case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes has same value that is equal to val3
     * in given case.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testRemovedEntryOnPrimaryWithMaxGridCacheVersionRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(true);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "MAX_GRID_CACHE_VERSION", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>PRINT_ONLY</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary: key is missing</li>
     * <li>Backup 1: key has ver1  and val1</li>
     * <li>Backup 2: key has ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * (PRINT_ONLY in given case) and doesn't contain the value that was used for fix.</li>
     * <li>Ensure that no repair was applied.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testRemovedEntryOnPrimaryWithPrintOnlyRepairAlg() throws Exception {
        List<ClusterNode> nodes = populateCacheWithInconsistentEntry(true);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "PRINT_ONLY", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        assertNull(((IgniteEx)grid(nodes.get(0))).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null));

        assertEquals(VALUE_PREFIX + INVALID_KEY,
            ((IgniteEx)grid(nodes.get(1))).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null));

        assertEquals(VALUE_PREFIX + INVALID_KEY,
            ((IgniteEx)grid(nodes.get(2))).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null));

        assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2,
            ((IgniteEx)grid(nodes.get(3))).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null));
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode.</li>
     * <p>
     * <b>Expected result:</b>
     * Cause there are no missing keys neither totally missing, nor available only in deferred delete queue, default
     * MAJORITY algorithm won't be used, MAX_GRID_CACHE_VERSION will be used instead.
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * MAX_GRID_CACHE_VERSION in given case algorithm and also contains the value that was used for fix (val3 in given
     * case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes  has same value that is equal to val3.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testMissedUpdateWithDefaultRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(false);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode",
            "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>MAJORITY</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * Cause there are no missing keys neither totally missing, nor available only in deferred delete queue, MAJORITY
     * algorithm won't be used, MAX_GRID_CACHE_VERSION will be used instead.
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * MAX_GRID_CACHE_VERSION in given case algorithm and also contains the value that was used for fix (val3 in given
     * case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes  has same value that is equal to val3.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testMissedUpdateOnPrimaryWithMajorityRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(false);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "MAJORITY", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>PRIMARY</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * Cause there are no missing keys neither totally missing, nor available only in deferred delete queue, PRIMARY
     * algorithm won't be used, MAX_GRID_CACHE_VERSION will be used instead.
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * MAX_GRID_CACHE_VERSION in given case algorithm and also contains the value that was used for fix (val3 in given
     * case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes  has same value that is equal to val3.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testMissedUpdateOnPrimaryWithPrimaryRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(false);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "PRIMARY", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>MAX_GRID_CACHE_VERSION</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * MAX_GRID_CACHE_VERSION in given case algorithm and also contains the value that was used for fix (val3 in given
     * case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes  has same value that is equal to val3.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testMissedUpdateOnPrimaryWithMaxGridCacheVersionRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(false);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "MAX_GRID_CACHE_VERSION", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2, val);
        }
    }

    /**
     * <b>Prerequisites:</b>
     * Start cluster with 4 nodes and create <b>atomic/transactional non-persistent/persistent</b> cache with 3 backups.
     * Populate cache with some data.
     * <p>
     * <b>Steps:</b>
     * <or>
     * <li>Add one more entry with inconsistent data:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Run partition_reconciliation command in fix mode with fix-alg <b>PRINT_ONLY</b>.</li>
     * <p>
     * <b>Expected result:</b>
     * Cause there are no missing keys neither totally missing, nor available only in deferred delete queue, PRINT_ONLY
     * algorithm won't be used, MAX_GRID_CACHE_VERSION will be used instead.
     * <ul>
     * <li>Parse output and ensure that one key was added to 'inconsistent keys section' with corresponding
     * versions and values:
     * <ul>
     * <li>Primary:  key has ver0  and val0</li>
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     * </li>
     * <li>Also ensure that key has repair meta info, that reflects that keys were fixed using expected algorithm
     * MAX_GRID_CACHE_VERSION in given case algorithm and also contains the value that was used for fix (val3 in given
     * case).</li>
     * <li>Ensure that previously inconsistent key on all 4 nodes  has same value that is equal to val3.</li>
     * </ul>
     * </or>
     *
     * @throws Exception If failed.
     */
    @Test
    public void testMissedUpdateOnPrimaryWithPrintOnlyRepairAlg() throws Exception {
        populateCacheWithInconsistentEntry(false);

        assertEquals(EXIT_CODE_OK, execute("--cache", "partition_reconciliation", "--fix-mode", "--fix-alg",
            "PRINT_ONLY", "--recheck-delay", "0"));

        // TODO: 12.12.19 Validate output.

        for (int i = 0; i < 4; i++) {
            Object val = ignite(i).cachex(DEFAULT_CACHE_NAME).localPeek(INVALID_KEY, null);

            assertEquals(VALUE_PREFIX + INVALID_KEY + BROKEN_POSTFIX_2, val);
        }
    }

    /**
     * Prepare inconsistent entry with following values and versions:
     * <ul>
     * {@code if(dropEntry)}
     * <li>Primary:  key is missing</li>
     * else
     * <li>Primary:  key has ver0  and val0</li>
     * In all cases:
     * <li>Backup 1: key has ver1 > ver1  and val1</li>
     * <li>Backup 2: key has ver1 > ver1  and val1 (same as backup 1)</li>
     * <li>Backup 3: key has ver2 > ver1  and val3</li>
     * </ul>
     *
     * @param dropEntry Boolean flag in order to define whether to drop entry on primary or not.
     * @return List of affinity nodes.
     */
    private List<ClusterNode> populateCacheWithInconsistentEntry(boolean dropEntry) {
        List<ClusterNode> nodes = ignite(0).cachex(DEFAULT_CACHE_NAME).cache().context().affinity().
            nodesByKey(
                INVALID_KEY,
                ignite(0).cachex(DEFAULT_CACHE_NAME).context().topology().readyTopologyVersion());

        ignite(0).cache(DEFAULT_CACHE_NAME).put(INVALID_KEY, VALUE_PREFIX + INVALID_KEY);

        if (dropEntry) {
            ((IgniteEx)grid(nodes.get(0))).cachex(DEFAULT_CACHE_NAME).
                clearLocallyAll(Collections.singleton(INVALID_KEY), true, true, true);
        }
        else {
            corruptDataEntry(((IgniteEx)grid(nodes.get(0))).cachex(
                DEFAULT_CACHE_NAME).context(),
                INVALID_KEY,
                false,
                false,
                new GridCacheVersion(0, 0, 1),
                BROKEN_POSTFIX_1);
        }

        corruptDataEntry(((IgniteEx)grid(nodes.get(1))).cachex(
            DEFAULT_CACHE_NAME).context(),
            INVALID_KEY,
            false,
            false,
            new GridCacheVersion(0, 0, 2),
            null);

        corruptDataEntry(((IgniteEx)grid(nodes.get(2))).cachex(
            DEFAULT_CACHE_NAME).context(),
            INVALID_KEY,
            false,
            false,
            new GridCacheVersion(0, 0, 2),
            null);

        corruptDataEntry(((IgniteEx)grid(nodes.get(3))).cachex(
            DEFAULT_CACHE_NAME).context(),
            INVALID_KEY,
            false,
            true,
            new GridCacheVersion(0, 0, 3),
            BROKEN_POSTFIX_2);

        return nodes;
    }

    /**
     * Corrupts data entry.
     *
     * @param ctx Context.
     * @param key Key.
     * @param breakCntr Break counter.
     * @param breakData Break data.
     * @param ver GridCacheVersion to use.
     * @param brokenValPostfix Postfix to add to value if breakData flag is set to true.
     */
    protected void corruptDataEntry(
        GridCacheContext<Object, Object> ctx,
        Object key,
        boolean breakCntr,
        boolean breakData,
        GridCacheVersion ver,
        String brokenValPostfix
    ) {
        int partId = ctx.affinity().partition(key);

        try {
            long updateCntr = ctx.topology().localPartition(partId).updateCounter();

            Object valToPut = ctx.cache().keepBinary().get(key);

            if (breakCntr)
                updateCntr++;

            if (breakData)
                valToPut = valToPut.toString() + brokenValPostfix;

            // Create data entry
            DataEntry dataEntry = new DataEntry(
                ctx.cacheId(),
                new KeyCacheObjectImpl(key, null, partId),
                new CacheObjectImpl(valToPut, null),
                GridCacheOperation.UPDATE,
                new GridCacheVersion(),
                ver,
                0L,
                partId,
                updateCntr
            );

            GridCacheDatabaseSharedManager db = (GridCacheDatabaseSharedManager)ctx.shared().database();

            db.checkpointReadLock();

            try {
                U.invoke(GridCacheDatabaseSharedManager.class, db, "applyUpdate", ctx, dataEntry,
                    false);
            }
            finally {
                db.checkpointReadUnlock();
            }
        }
        catch (IgniteCheckedException e) {
            e.printStackTrace();
        }
    }
}
