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

package org.apache.ignite.internal.util;

import org.apache.commons.io.FileSystemUtils;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** */
public class IgniteUtilsWorkDirectoryTest {

//    /** */
//    private static String TEMP_DIRECTORY = null;
//
//    static {
//        try {
//
//            TEMP_DIRECTORY = Files.createTempDirectory("WorkDirectoryTest").toFile().getAbsolutePath();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    /** */
    private static final String USER_WORK_DIR = String.join(File.separator, U.getIgniteHome() , "userWorkDirTest");

    /** */
    private static final String USER_IGNITE_HOME = String.join(File.separator, U.getIgniteHome() , "userIgniteHomeTest");

    /** */
    private static final String USER_DIR_PROPERTY_VALUE = String.join(File.separator,new File(U.getIgniteHome()).getParent(), "userDirPropertyTest");

    /** */
    private static String dfltIgniteHome;

    /** */
    private static String dfltUserDir;

    /** */
    @After
    public void setup() {
        dfltIgniteHome = System.getProperty(IgniteSystemProperties.IGNITE_HOME);
        dfltUserDir = System.getProperty("user.dir");
        System.clearProperty(IgniteSystemProperties.IGNITE_HOME);
        System.clearProperty("user.dir");
    }

    /** */
    @After
    public void tearDown() {
        if (dfltIgniteHome != null)
            System.setProperty(IgniteSystemProperties.IGNITE_HOME, dfltIgniteHome);
        if (dfltUserDir != null)
            System.setProperty("user.dir", dfltUserDir);
    }

    /** */
    @Test
    public void testWorkDirectory1() {
        genericWorkDirectoryTest(true, false, false,
                USER_WORK_DIR);
    }

    /** */
    @Test
    public void testWorkDirectory2() {
        genericWorkDirectoryTest(true, false, true,
                USER_WORK_DIR);
    }

    /** */
    @Test
    public void testWorkDirectory3() {
        genericWorkDirectoryTest(true, true, false,
                USER_WORK_DIR);
    }

    /** */
    @Test
    public void testWorkDirectory4() {
        genericWorkDirectoryTest(true, true, true,
                USER_WORK_DIR);
    }

    /** */
    private void genericWorkDirectoryTest(boolean userWorkDirFlag, boolean userIgniteHomeFlag,
                                          boolean userDirPropFlag, String expWorkDir) {
        if (userDirPropFlag)
            System.setProperty("user.dir", USER_DIR_PROPERTY_VALUE);
        else
            System.clearProperty("user.dir");

        String userWorkDir = "";
        if (userWorkDirFlag)
            userWorkDir = USER_WORK_DIR;

        U.nullifyHomeDirectory();
        System.clearProperty(IgniteSystemProperties.IGNITE_HOME);
        String userIgniteHome = "";
        if (userIgniteHomeFlag)
            userIgniteHome = USER_IGNITE_HOME;

        String actualWorkDir = null;
        try {
            actualWorkDir = IgniteUtils.workDirectory(userWorkDir, userIgniteHome);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        assert expWorkDir.equals(actualWorkDir) : "actualWorkDir: " + actualWorkDir + ", expectedWorkDir: " + expWorkDir;

    }

    /** */
    @Test
    public void nonAbsolutePathTest() {
        genericPathExceptionTest("nonAbsolutePathTestDirectory",
                "Work directory path must be absolute: nonAbsolutePathTestDirectory");
    }

    /** */
    @Test
    public void workDirCannotWriteTest() {
        String strDir = String.join(File.separator, USER_WORK_DIR, "CannotWriteTestDirectory");
        File dir = new File(strDir);
        X.println("exists? " + dir.exists());
        if (dir.exists()) {
            boolean delete = deleteDirectory(dir);
            X.println("deleted? " + delete);
        }
        dir.mkdirs();

        boolean permission = dir.setWritable(false, false);
        assert permission : "No permission";
        try {
            Runtime.getRuntime().exec("chmod 444 " + strDir);
        } catch (IOException e) {
            X.println("chmod failed");
            e.printStackTrace();
        }
        assert dir.exists() : "Work directory was not created";
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        X.println("111 " + dir.isAbsolute());
        X.println("222 " + dir.canRead());
        X.println("333 " + dir.canWrite());

        genericPathExceptionTest(strDir, "Cannot write to work directory: " + strDir);
    }

    static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    /** */
    @Test
    public void workDirCannotReadTest() {
        String strDir = String.join(File.separator, USER_WORK_DIR, "CannotReadTestDirectory");
        File dir = new File(strDir);
        dir.mkdirs();
        dir.setReadable(false);
        assert dir.exists() : "Work directory was not created";

        genericPathExceptionTest(strDir, "Cannot read from work directory: " + strDir);
    }

    /** */
    @Test
    public void workDirNotExistAndCannotBeCreatedTest() {
        String strDirParent = String.join(File.separator, USER_WORK_DIR, "CannotWriteTestDirectory");
        File dirParent = new File(strDirParent);
        dirParent.mkdirs();
        dirParent.setWritable(false);
        assert dirParent.exists() : "Work directory was not created";

        String strDir = String.join(File.separator, strDirParent, "newDirectory");

        genericPathExceptionTest(strDir,
                "Work directory does not exist and cannot be created: " + strDir);
    }

    /** */
    private void genericPathExceptionTest(String userWorkDir, String expMsg) {
        String actualWorkDir = null;
        boolean fail = false;

        try {
            actualWorkDir = IgniteUtils.workDirectory(userWorkDir, null);
        } catch (IgniteCheckedException e) {
            assert e.getMessage().contains(expMsg) : "expected IgniteCheckedException with " + expMsg + " in message";
            fail = true;
        }

        assert fail : "actualWorkDir: " + actualWorkDir + ", expected: thrown exception";
    }

}
