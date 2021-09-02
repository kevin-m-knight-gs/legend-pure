package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.factory.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class TestDistributedBinaryMetadata
{
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
            Assert.assertEquals("Invalid metadata name: \"" + name + "\"", e.getMessage());
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
}
