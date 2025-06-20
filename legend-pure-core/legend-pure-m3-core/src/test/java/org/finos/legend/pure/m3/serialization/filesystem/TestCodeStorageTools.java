// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.m3.serialization.filesystem;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.RepositoryCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.welcome.WelcomeCodeStorage;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestCodeStorageTools
{
    @Test
    public void testIsRootPath()
    {
        Assert.assertTrue(CodeStorageTools.isRootPath(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertTrue(CodeStorageTools.isRootPath("/"));

        Assert.assertFalse(CodeStorageTools.isRootPath(null));
        Assert.assertFalse(CodeStorageTools.isRootPath(""));
        Assert.assertFalse(CodeStorageTools.isRootPath("/definitely/not/the/root/path"));
        Assert.assertFalse(CodeStorageTools.isRootPath("not even a valid path"));
        Assert.assertFalse(CodeStorageTools.isRootPath("root"));
        Assert.assertFalse(CodeStorageTools.isRootPath("root.pure"));
    }

    @Test
    public void testIsValidPath()
    {
        Assert.assertTrue(CodeStorageTools.isValidPath(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertTrue(CodeStorageTools.isValidPath(WelcomeCodeStorage.WELCOME_FILE_PATH));
        Assert.assertTrue(CodeStorageTools.isValidPath(WelcomeCodeStorage.WELCOME_FILE_NAME));
        Assert.assertTrue(CodeStorageTools.isValidPath("platform"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/platform"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/platform/pure/corefunctions/"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/platform/pure/corefunctions/lang.pure"));
        Assert.assertTrue(CodeStorageTools.isValidPath("nonexistent/but/still/valid"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid/"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid.pure"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid.csv"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid.css"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid.html"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid.js"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/nonexistent/but/still/valid.json"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/Valid/path/Including/_somE_/capITAL/LETTERS_and_UNDERscoreS.txt"));
        Assert.assertTrue(CodeStorageTools.isValidPath("/v4l1d/p4th/1nclud1ng/numb3rs.num"));

        Assert.assertFalse(CodeStorageTools.isValidPath(null));
        Assert.assertFalse(CodeStorageTools.isValidPath(""));
        Assert.assertFalse(CodeStorageTools.isValidPath("not a valid path"));
        Assert.assertFalse(CodeStorageTools.isValidPath("/not a valid path/even though it/almost looks like one"));
        Assert.assertFalse(CodeStorageTools.isValidPath("/path/with/special/$#/chars"));
        Assert.assertFalse(CodeStorageTools.isValidPath("/path/with/an/empty//element"));
        Assert.assertFalse(CodeStorageTools.isValidPath("//"));
        Assert.assertFalse(CodeStorageTools.isValidPath("//another/path/with/an/empty/element"));
        Assert.assertFalse(CodeStorageTools.isValidPath("/path/with/empty/file/extension."));
        Assert.assertFalse(CodeStorageTools.isValidPath("empty_file_extension."));
        Assert.assertFalse(CodeStorageTools.isValidPath("/invalid/file/extension.-abc"));
        Assert.assertFalse(CodeStorageTools.isValidPath("/missing/file/name/.pure"));
    }

    @Test
    public void testIsValidFilePath()
    {
        Assert.assertTrue(CodeStorageTools.isValidFilePath(WelcomeCodeStorage.WELCOME_FILE_PATH));
        Assert.assertTrue(CodeStorageTools.isValidFilePath(WelcomeCodeStorage.WELCOME_FILE_NAME));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/platform/pure/corefunctions/lang.pure"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("nonexistent/but/still/valid/file.pure"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/file.pure"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/file.csv"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/file.css"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/file.html"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/file.js"));
        Assert.assertTrue(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/file.json"));

        Assert.assertFalse(CodeStorageTools.isValidFilePath(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("/platform/pure/corefunctions"));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("/platform/pure/corefunctions/"));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("nonexistent/but/still/valid/folder"));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/folder"));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("/nonexistent/but/still/valid/folder/"));

        Assert.assertFalse(CodeStorageTools.isValidFilePath(null));
        Assert.assertFalse(CodeStorageTools.isValidFilePath(""));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("not a valid path"));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("/not a valid path/even though it/almost looks like one"));
        Assert.assertFalse(CodeStorageTools.isValidFilePath("/path/with/special/$#/chars"));
    }

    @Test
    public void testIsValidFolderPath()
    {
        Assert.assertTrue(CodeStorageTools.isValidFolderPath(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertTrue(CodeStorageTools.isValidFolderPath("/platform/pure/corefunctions"));
        Assert.assertTrue(CodeStorageTools.isValidFolderPath("/platform/pure/corefunctions/"));
        Assert.assertTrue(CodeStorageTools.isValidFolderPath("nonexistent/but/still/valid/folder"));
        Assert.assertTrue(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/folder"));
        Assert.assertTrue(CodeStorageTools.isValidFolderPath("nonexistent/but/still/valid/folder/"));
        Assert.assertTrue(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/folder/"));

        Assert.assertFalse(CodeStorageTools.isValidFolderPath(WelcomeCodeStorage.WELCOME_FILE_PATH));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath(WelcomeCodeStorage.WELCOME_FILE_NAME));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/platform/pure/corefunctions/lang.pure"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/file.pure"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/file.csv"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/file.css"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/file.html"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/file.js"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/nonexistent/but/still/valid/file.json"));

        Assert.assertFalse(CodeStorageTools.isValidFolderPath(null));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath(""));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("not a valid path"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/not a valid path/even though it/almost looks like one"));
        Assert.assertFalse(CodeStorageTools.isValidFolderPath("/path/with/special/$#/chars"));
    }

    @Test
    public void testIsPureFilePath()
    {
        Assert.assertTrue(CodeStorageTools.isPureFilePath(WelcomeCodeStorage.WELCOME_FILE_PATH));
        Assert.assertTrue(CodeStorageTools.isPureFilePath(WelcomeCodeStorage.WELCOME_FILE_NAME));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/platform/pure/corefunctions/lang.pure"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("nonexistent/but/still/valid/file.pure"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.pure"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.Pure"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.pUre"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.puRe"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.purE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PUre"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PuRe"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PurE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.pURe"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.pUrE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.puRE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PURe"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PUrE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PuRE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.pURE"));
        Assert.assertTrue(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.PURE"));

        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.csv"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.css"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.html"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.js"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/file.json"));

        Assert.assertFalse(CodeStorageTools.isPureFilePath(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/platform/pure/corefunctions"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/platform/pure/corefunctions/"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/folder"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/nonexistent/but/still/valid/folder/"));

        Assert.assertFalse(CodeStorageTools.isPureFilePath(null));
        Assert.assertFalse(CodeStorageTools.isPureFilePath(""));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("not a valid path"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/not a valid path/even though it/almost looks like one"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("/path/with/special/$#/chars"));
        Assert.assertFalse(CodeStorageTools.isPureFilePath("\\not\\a\\valid\\path\\but\\still\\has\\extension.pure"));
    }

    @Test
    public void testHasPureFileExtension()
    {
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension(WelcomeCodeStorage.WELCOME_FILE_PATH));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension(WelcomeCodeStorage.WELCOME_FILE_NAME));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/platform/pure/corefunctions/lang.pure"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("nonexistent/but/still/valid/file.pure"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.pure"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.Pure"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.pUre"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.puRe"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.purE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PUre"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PuRe"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PurE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.pURe"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.pUrE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.puRE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PURe"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PUrE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PuRE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.pURE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.PURE"));
        Assert.assertTrue(CodeStorageTools.hasPureFileExtension("\\not\\a\\valid\\path\\but\\still\\has\\extension.pure"));

        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.csv"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.css"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.html"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.js"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/file.json"));

        Assert.assertFalse(CodeStorageTools.hasPureFileExtension(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/platform/pure/corefunctions"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/platform/pure/corefunctions/"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/folder"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/nonexistent/but/still/valid/folder/"));

        Assert.assertFalse(CodeStorageTools.hasPureFileExtension(null));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension(""));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("not a valid path"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/not a valid path/even though it/almost looks like one"));
        Assert.assertFalse(CodeStorageTools.hasPureFileExtension("/path/with/special/$#/chars"));
    }

    @Test
    public void testGetInitialPathElement()
    {
        Assert.assertNull(CodeStorageTools.getInitialPathElement(null));
        Assert.assertNull(CodeStorageTools.getInitialPathElement(""));

        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform/"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform/"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform/pure"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform/pure"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform/pure/"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform/pure/"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform/pure/corefunctions"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform/pure/corefunctions"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform/pure/corefunctions/"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform/pure/corefunctions/"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("/platform/pure/corefunctions/lang.pure"));
        Assert.assertEquals("platform", CodeStorageTools.getInitialPathElement("platform/pure/corefunctions/lang.pure"));
        Assert.assertEquals(null, CodeStorageTools.getInitialPathElement("welcome.pure"));
        Assert.assertEquals(null, CodeStorageTools.getInitialPathElement("/welcome.pure"));
        Assert.assertEquals(null, CodeStorageTools.getInitialPathElement("/welcome.pure/"));
    }

    @Test
    public void testPathStartsWithElement()
    {
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform/", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform/", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform/pure", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform/pure", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform/pure/", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform/pure/", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform/pure/corefunctions", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform/pure/corefunctions", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform/pure/corefunctions/", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform/pure/corefunctions/", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("/platform/pure/corefunctions/lang.pure", "platform"));
        Assert.assertTrue(CodeStorageTools.pathStartsWithElement("platform/pure/corefunctions/lang.pure", "platform"));

        Assert.assertFalse(CodeStorageTools.pathStartsWithElement(null, "anything"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("", "anything"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platform/pure/corefunctions/lang.pure", "core"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platform/pure/corefunctions/lang.pure", "pure"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platform/pure/corefunctions/lang.pure", "corefunctions"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("abc", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("welcome.pure", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/welcome.pure", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("platfor", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platfor", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platfor/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("platfor/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("platforms", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platforms", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platforms/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("platforms/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("platforn", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platforn", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/platforn/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("platforn/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("plotfarm", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/plotfarm", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("/plotfarm/", "platform"));
        Assert.assertFalse(CodeStorageTools.pathStartsWithElement("plotfarm/", "platform"));
    }

    @Test
    public void testForEachPathElement()
    {
        assertForEachPathElement(null);
        assertForEachPathElement("");
        assertForEachPathElement("/");

        assertForEachPathElement("platform", "platform");
        assertForEachPathElement("/platform", "platform");
        assertForEachPathElement("platform/", "platform");
        assertForEachPathElement("/platform/", "platform");

        assertForEachPathElement("platform/pure", "platform", "pure");
        assertForEachPathElement("/platform/pure", "platform", "pure");
        assertForEachPathElement("platform/pure/", "platform", "pure");
        assertForEachPathElement("/platform/pure/", "platform", "pure");

        assertForEachPathElement("platform/pure/corefunctions/lang.pure", "platform", "pure", "corefunctions", "lang.pure");
        assertForEachPathElement("/platform/pure/corefunctions/lang.pure", "platform", "pure", "corefunctions", "lang.pure");
    }

    private void assertForEachPathElement(String path, String... expectedElements)
    {
        MutableList<String> actual = Lists.mutable.empty();
        CodeStorageTools.forEachPathElement(path, actual::add);
        Assert.assertEquals(Arrays.asList(expectedElements), actual);
    }

    @Test
    public void testSplitPath()
    {
        Assert.assertEquals(Lists.immutable.empty(), CodeStorageTools.splitPath(null));
        Assert.assertEquals(Lists.immutable.empty(), CodeStorageTools.splitPath(""));
        Assert.assertEquals(Lists.immutable.empty(), CodeStorageTools.splitPath("/"));

        Assert.assertEquals(Lists.mutable.with("platform"), CodeStorageTools.splitPath("platform"));
        Assert.assertEquals(Lists.mutable.with("platform"), CodeStorageTools.splitPath("/platform"));
        Assert.assertEquals(Lists.mutable.with("platform"), CodeStorageTools.splitPath("platform/"));
        Assert.assertEquals(Lists.mutable.with("platform"), CodeStorageTools.splitPath("/platform/"));

        Assert.assertEquals(Lists.mutable.with("platform", "pure"), CodeStorageTools.splitPath("platform/pure"));
        Assert.assertEquals(Lists.mutable.with("platform", "pure"), CodeStorageTools.splitPath("/platform/pure"));
        Assert.assertEquals(Lists.mutable.with("platform", "pure"), CodeStorageTools.splitPath("platform/pure/"));
        Assert.assertEquals(Lists.mutable.with("platform", "pure"), CodeStorageTools.splitPath("/platform/pure/"));

        Assert.assertEquals(Lists.mutable.with("platform", "pure", "corefunctions", "lang.pure"), CodeStorageTools.splitPath("platform/pure/corefunctions/lang.pure"));
        Assert.assertEquals(Lists.mutable.with("platform", "pure", "corefunctions", "lang.pure"), CodeStorageTools.splitPath("/platform/pure/corefunctions/lang.pure"));
    }

    @Test
    public void testCanonicalizePath()
    {
        Assert.assertEquals(RepositoryCodeStorage.ROOT_PATH, CodeStorageTools.canonicalizePath(null));
        Assert.assertEquals(RepositoryCodeStorage.ROOT_PATH, CodeStorageTools.canonicalizePath(""));
        Assert.assertEquals(RepositoryCodeStorage.ROOT_PATH, CodeStorageTools.canonicalizePath("     \t   \t\t"));

        Assert.assertEquals("/platform", CodeStorageTools.canonicalizePath("platform"));
        Assert.assertEquals("/platform", CodeStorageTools.canonicalizePath("/platform"));
        Assert.assertEquals("/platform/pure/corefunctions/lang.pure", CodeStorageTools.canonicalizePath("platform/pure/corefunctions/lang.pure"));
        Assert.assertEquals("/platform/pure/corefunctions/lang.pure", CodeStorageTools.canonicalizePath("/platform/pure/corefunctions/lang.pure"));
        Assert.assertEquals("/platform/pure/corefunctions/lang.pure", CodeStorageTools.canonicalizePath("     /platform/pure/corefunctions/lang.pure\t\t"));
    }

    @Test
    public void testIsCanonicalPath()
    {
        Assert.assertTrue(CodeStorageTools.isCanonicalPath(RepositoryCodeStorage.ROOT_PATH));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath(WelcomeCodeStorage.WELCOME_FILE_PATH));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/platform/pure/corefunctions"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/platform/pure/corefunctions/lang.pure"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid.pure"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid.csv"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid.css"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid.html"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid.js"));
        Assert.assertTrue(CodeStorageTools.isCanonicalPath("/nonexistent/but/still/valid.json"));

        Assert.assertFalse(CodeStorageTools.isCanonicalPath(null));
        Assert.assertFalse(CodeStorageTools.isCanonicalPath(""));
        Assert.assertFalse(CodeStorageTools.isCanonicalPath("not a valid path"));
        Assert.assertFalse(CodeStorageTools.isCanonicalPath("/not a valid path/even though it/almost looks like one"));
        Assert.assertFalse(CodeStorageTools.isCanonicalPath("/path/with/special/$#/chars"));
        Assert.assertFalse(CodeStorageTools.isCanonicalPath("valid/but/not/canonical"));
        Assert.assertFalse(CodeStorageTools.isCanonicalPath("valid/but/not/canonical/"));
    }

    @Test
    public void testJoinPaths()
    {
        Assert.assertEquals("", CodeStorageTools.joinPaths());
        Assert.assertEquals("", CodeStorageTools.joinPaths((String[])null));

        Assert.assertEquals("platform", CodeStorageTools.joinPaths("platform"));
        Assert.assertEquals("/platform", CodeStorageTools.joinPaths("/platform"));
        Assert.assertEquals("platform/", CodeStorageTools.joinPaths("platform/"));
        Assert.assertEquals("/platform/", CodeStorageTools.joinPaths("/platform/"));

        Assert.assertEquals("platform/pure/corefunctions", CodeStorageTools.joinPaths("platform", "pure", "corefunctions"));
        Assert.assertEquals("/platform/pure/corefunctions/", CodeStorageTools.joinPaths("/platform", "pure", "corefunctions/"));
        Assert.assertEquals("/platform/pure/corefunctions/", CodeStorageTools.joinPaths("/platform", "/pure/", "corefunctions/"));
        Assert.assertEquals("/platform/pure/corefunctions/", CodeStorageTools.joinPaths("/platform/", "pure/", "/corefunctions/"));

        Assert.assertEquals("platform/pure/corefunctions/lang.pure", CodeStorageTools.joinPaths("platform", "pure", "corefunctions", "lang.pure"));
        Assert.assertEquals("/platform/pure/corefunctions/lang.pure", CodeStorageTools.joinPaths("/platform", "pure", "/corefunctions", "lang.pure"));
    }
}
