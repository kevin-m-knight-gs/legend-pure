package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class TestDistributedBinaryMetadata
{
    @ClassRule
    public static final TemporaryFolder TMP = new TemporaryFolder();

    @Test
    public void testWithNoDependencies()
    {
        DistributedBinaryMetadata metadata = DistributedBinaryMetadata.newMetadata("abc");
        Assert.assertEquals("abc", metadata.getName());
        Assert.assertEquals(Collections.emptySet(), metadata.getDependencies());
        Assert.assertThrows(UnsupportedOperationException.class, () -> metadata.getDependencies().add("something"));
    }

    @Test
    public void testWithDependenciesAsVarArgs()
    {
        DistributedBinaryMetadata metadata = DistributedBinaryMetadata.newMetadata("def", "ghi", "jkl");
        Assert.assertEquals("def", metadata.getName());
        Assert.assertEquals(Sets.mutable.with("ghi", "jkl"), metadata.getDependencies());
        Assert.assertThrows(UnsupportedOperationException.class, () -> metadata.getDependencies().add("something"));
    }

    @Test
    public void testWithDependenciesAsIterable()
    {
        DistributedBinaryMetadata metadata = DistributedBinaryMetadata.newMetadata("mno", Sets.mutable.with("pqr", "stu"));
        Assert.assertEquals("mno", metadata.getName());
        Assert.assertEquals(Sets.mutable.with("pqr", "stu"), metadata.getDependencies());
        Assert.assertThrows(UnsupportedOperationException.class, () -> metadata.getDependencies().add("something"));
    }

    @Test
    public void testInvalidMetadataName()
    {
        String[] invalidNames = {"", "$$%", "invalid name"};
        for (String name : invalidNames)
        {
            IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> DistributedBinaryMetadata.newMetadata(name));
            Assert.assertEquals(name, "Invalid metadata name: \"" + name + "\"", e.getMessage());
        }

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> DistributedBinaryMetadata.newMetadata(null));
        Assert.assertEquals("Invalid metadata name: null", e.getMessage());
    }

    @Test
    public void testInvalidDependencies()
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> DistributedBinaryMetadata.newMetadata("a", "b", "c", null, "d"));
        Assert.assertEquals("Dependencies may not contain null", e.getMessage());
    }

    @Test
    public void testReadWriteMetadata() throws IOException
    {
        Set<DistributedBinaryMetadata> metadata = Sets.mutable.with(DistributedBinaryMetadata.newMetadata("abc"), DistributedBinaryMetadata.newMetadata("def", "abc"), DistributedBinaryMetadata.newMetadata("ghi", "abc", "def"));

        Path directory = TMP.newFolder().toPath();
        List<Path> paths = DistributedBinaryMetadata.writeMetadataDefinitions(directory, metadata);
        Assert.assertEquals(
                Sets.mutable.with(
                        directory.resolve(Paths.get("metadata", "definitions", "abc.json")),
                        directory.resolve(Paths.get("metadata", "definitions", "def.json")),
                        directory.resolve(Paths.get("metadata", "definitions", "ghi.json"))),
                Sets.mutable.withAll(paths));

        for (DistributedBinaryMetadata m : metadata)
        {
            Path file = directory.resolve(Paths.get("metadata", "definitions", m.getName() + ".json"));
            DistributedBinaryMetadata loaded = DistributedBinaryMetadata.readMetadata(file);
            Assert.assertEquals(m.getName(), m, loaded);
        }
    }

    @Test
    public void testLoadMetadata_CurrentClassLoader()
    {
        Assert.assertEquals(Collections.emptyList(), DistributedBinaryMetadata.loadAllMetadata(Thread.currentThread().getContextClassLoader()));

        RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> DistributedBinaryMetadata.loadMetadata(Thread.currentThread().getContextClassLoader(), "non_existent"));
        Assert.assertEquals("Cannot find metadata \"non_existent\" (resource name \"metadata/definitions/non_existent.json\")", e.getMessage());
    }

    @Test
    public void testLoadMetadata_Directories() throws IOException
    {
        List<DistributedBinaryMetadata> dir1Metadata = Lists.fixedSize.with(DistributedBinaryMetadata.newMetadata("abc"), DistributedBinaryMetadata.newMetadata("def", "abc"));
        List<DistributedBinaryMetadata> dir2Metadata = Lists.fixedSize.with(DistributedBinaryMetadata.newMetadata("ghi", "def"), DistributedBinaryMetadata.newMetadata("jkl", "xyz"));

        Path dir1 = TMP.newFolder().toPath();
        DistributedBinaryMetadata.writeMetadataDefinitions(dir1, dir1Metadata);

        Path dir2 = TMP.newFolder().toPath();
        DistributedBinaryMetadata.writeMetadataDefinitions(dir2, dir2Metadata);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{dir1.toUri().toURL(), dir2.toUri().toURL()}))
        {
            Assert.assertEquals(Sets.mutable.withAll(dir1Metadata).withAll(dir2Metadata), Sets.mutable.withAll(DistributedBinaryMetadata.loadAllMetadata(classLoader)));

            Assert.assertEquals(Lists.mutable.with(DistributedBinaryMetadata.newMetadata("abc")), DistributedBinaryMetadata.loadMetadata(classLoader, "abc"));

            Assert.assertEquals(Sets.mutable.withAll(dir1Metadata).with(dir2Metadata.get(0)), Sets.mutable.withAll(DistributedBinaryMetadata.loadMetadata(classLoader, "ghi")));

            RuntimeException e1 = Assert.assertThrows(RuntimeException.class, () -> DistributedBinaryMetadata.loadMetadata(classLoader, "ghi", "jkl"));
            Assert.assertEquals("Cannot find metadata \"xyz\" (resource name \"metadata/definitions/xyz.json\")", e1.getMessage());

            RuntimeException e2 = Assert.assertThrows(RuntimeException.class, () -> DistributedBinaryMetadata.loadMetadata(classLoader, "ghi", "mno"));
            Assert.assertEquals("Cannot find metadata \"mno\" (resource name \"metadata/definitions/mno.json\")", e2.getMessage());
        }
    }

    @Test
    public void testLoadMetadata_Jars() throws IOException
    {
        List<DistributedBinaryMetadata> jar1Metadata = Lists.fixedSize.with(DistributedBinaryMetadata.newMetadata("abc"), DistributedBinaryMetadata.newMetadata("def", "abc"));
        List<DistributedBinaryMetadata> jar2Metadata = Lists.fixedSize.with(DistributedBinaryMetadata.newMetadata("ghi", "def"), DistributedBinaryMetadata.newMetadata("jkl", "xyz"));

        Path dir = TMP.newFolder().toPath();
        Path jar1 = dir.resolve("jar1.jar");
        Path jar2 = dir.resolve("jar2.jar");

        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar1))))
        {
            jarStream.putNextEntry(new ZipEntry("metadata/"));
            jarStream.closeEntry();
            jarStream.putNextEntry(new ZipEntry("metadata/definitions/"));
            jarStream.closeEntry();
            DistributedBinaryMetadata.writeMetadataDefinitions(jarStream, jar1Metadata);
        }

        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar2))))
        {
            jarStream.putNextEntry(new ZipEntry("metadata/"));
            jarStream.closeEntry();
            jarStream.putNextEntry(new ZipEntry("metadata/definitions/"));
            jarStream.closeEntry();
            DistributedBinaryMetadata.writeMetadataDefinitions(jarStream, jar2Metadata);
        }

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar1.toUri().toURL(), jar2.toUri().toURL()}))
        {
            Assert.assertEquals(Sets.mutable.withAll(jar1Metadata).withAll(jar2Metadata), Sets.mutable.withAll(DistributedBinaryMetadata.loadAllMetadata(classLoader)));

            Assert.assertEquals(Lists.mutable.with(DistributedBinaryMetadata.newMetadata("abc")), DistributedBinaryMetadata.loadMetadata(classLoader, "abc"));

            Assert.assertEquals(Sets.mutable.withAll(jar1Metadata).with(jar2Metadata.get(0)), Sets.mutable.withAll(DistributedBinaryMetadata.loadMetadata(classLoader, "ghi")));

            RuntimeException e1 = Assert.assertThrows(RuntimeException.class, () -> DistributedBinaryMetadata.loadMetadata(classLoader, "ghi", "jkl"));
            Assert.assertEquals("Cannot find metadata \"xyz\" (resource name \"metadata/definitions/xyz.json\")", e1.getMessage());

            RuntimeException e2 = Assert.assertThrows(RuntimeException.class, () -> DistributedBinaryMetadata.loadMetadata(classLoader, "ghi", "mno"));
            Assert.assertEquals("Cannot find metadata \"mno\" (resource name \"metadata/definitions/mno.json\")", e2.getMessage());
        }
    }
}
