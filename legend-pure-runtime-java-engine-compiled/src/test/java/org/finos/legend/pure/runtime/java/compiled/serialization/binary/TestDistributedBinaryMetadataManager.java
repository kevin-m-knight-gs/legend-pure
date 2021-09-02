package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class TestDistributedBinaryMetadataManager
{
    @Test
    public void testEmpty()
    {
        DistributedBinaryMetadataManager manager = DistributedBinaryMetadataManager.fromMetadata();
        assertEmptyClosures(manager);
        assertUnknownMetadata(manager, "not_metadata");
    }

    @Test
    public void testSingleNoDependencies()
    {
        String metadataName = "some_metadata";
        DistributedBinaryMetadataManager manager = DistributedBinaryMetadataManager.fromMetadata(newMetadata(metadataName));

        assertEmptyClosures(manager);
        assertUnknownMetadata(manager, "unknown_metadata");

        Assert.assertEquals(Collections.singleton(metadataName), manager.getAllMetadataNames());
        Assert.assertTrue(manager.hasMetadata(metadataName));

        Assert.assertEquals(Collections.singleton(metadataName), manager.computeMetadataClosure(metadataName));
        Assert.assertEquals(Collections.singleton(metadataName), manager.computeMetadataClosure(metadataName, metadataName));
    }

    @Test
    public void testManyNoDependencies()
    {
        MutableList<String> metadataNames = Lists.mutable.with("some_metadata", "some_other_metadata", "one_more");
        DistributedBinaryMetadataManager manager = DistributedBinaryMetadataManager.fromMetadata(metadataNames.collect(this::newMetadata));

        assertEmptyClosures(manager);
        assertUnknownMetadata(manager, "unknown_metadata");

        Assert.assertEquals(metadataNames.toSet(), manager.getAllMetadataNames());
        Assert.assertEquals(Collections.emptyList(), metadataNames.reject(manager::hasMetadata));

        metadataNames.forEach(metadataName ->
        {
            Assert.assertEquals(Collections.singleton(metadataName), manager.computeMetadataClosure(metadataName));
            Assert.assertEquals(Collections.singleton(metadataName), manager.computeMetadataClosure(metadataName, metadataName));
        });

        Assert.assertEquals(metadataNames.toSet(), manager.computeMetadataClosure(metadataNames));
    }

    @Test
    public void testManyWithDependencies()
    {
        DistributedBinaryMetadataManager manager = DistributedBinaryMetadataManager.fromMetadata(
                newMetadata("m1", "m2"),
                newMetadata("m2", "m3", "m4"),
                newMetadata("m3"),
                newMetadata("m4", "m3", "m5"),
                newMetadata("m5")
        );

        assertEmptyClosures(manager);
        assertUnknownMetadata(manager, "m0");

        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.getAllMetadataNames());
        Assert.assertTrue(manager.hasMetadata("m1"));
        Assert.assertTrue(manager.hasMetadata("m2"));
        Assert.assertTrue(manager.hasMetadata("m3"));
        Assert.assertTrue(manager.hasMetadata("m4"));
        Assert.assertTrue(manager.hasMetadata("m5"));

        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m3"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m3", "m4"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m3", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m3", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m4"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m2", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m3"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m3", "m4"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m3", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m3", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m4"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m1", "m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m1", "m5"));

        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m3"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m3", "m4"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m3", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m3", "m5"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m4"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m2", "m3", "m4", "m5"), manager.computeMetadataClosure("m2", "m5"));

        Assert.assertEquals(Sets.mutable.with("m3"), manager.computeMetadataClosure("m3"));
        Assert.assertEquals(Sets.mutable.with("m3", "m4", "m5"), manager.computeMetadataClosure("m3", "m4"));
        Assert.assertEquals(Sets.mutable.with("m3", "m4", "m5"), manager.computeMetadataClosure("m3", "m4", "m5"));
        Assert.assertEquals(Sets.mutable.with("m3", "m5"), manager.computeMetadataClosure("m3", "m5"));

        Assert.assertEquals(Sets.mutable.with("m3", "m4", "m5"), manager.computeMetadataClosure("m4"));
        Assert.assertEquals(Sets.mutable.with("m3", "m4", "m5"), manager.computeMetadataClosure("m4", "m5"));

        Assert.assertEquals(Sets.mutable.with("m5"), manager.computeMetadataClosure("m5"));
    }

    @Test
    public void testMissingDependencies()
    {
        assertInvalidManagerCreation("Metadata \"m1\" is missing dependency \"m2\"", newMetadata("m1", "m2"));

        assertInvalidManagerCreation("Metadata \"m1\" is missing dependencies: \"m2\", \"m3\"", newMetadata("m1", "m2", "m3"));
        assertInvalidManagerCreation("Metadata \"m1\" is missing dependencies: \"m2\", \"m3\"", newMetadata("m1", "m3", "m2"));

        assertInvalidManagerCreation("Metadata \"m1\" is missing dependencies: \"m2\", \"m4\"", newMetadata("m1", "m2", "m3", "m4"), newMetadata("m3"));
        assertInvalidManagerCreation("Metadata \"m1\" is missing dependencies: \"m2\", \"m4\"", newMetadata("m1", "m4", "m3", "m2"), newMetadata("m3"));

        assertInvalidManagerCreation("Metadata \"m2\" is missing dependency \"m4\"", newMetadata("m1", "m2", "m3"), newMetadata("m2", "m3", "m4"), newMetadata("m3"));
    }

    @Test
    public void testInvalidMetadataName()
    {
        assertInvalidManagerCreation("Invalid metadata name: null", newMetadata(null));
        assertInvalidManagerCreation("Invalid metadata name: \"\"", newMetadata(""));
        assertInvalidManagerCreation("Invalid metadata name: \"$$%\"", newMetadata("$$%"));
        assertInvalidManagerCreation("Invalid metadata name: \"invalid name\"", newMetadata("valid_name"), newMetadata("invalid name"));
    }

    private void assertInvalidManagerCreation(String expectedMessage, DistributedBinaryMetadata... metadata)
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> DistributedBinaryMetadataManager.fromMetadata(metadata));
        Assert.assertEquals(expectedMessage, e.getMessage());
    }

    private void assertEmptyClosures(DistributedBinaryMetadataManager manager)
    {
        Assert.assertEquals(Collections.emptySet(), manager.computeMetadataClosure());
        Assert.assertEquals(Collections.emptySet(), manager.computeMetadataClosure(Collections.emptySet()));
    }

    private void assertUnknownMetadata(DistributedBinaryMetadataManager manager, String unknownMetadataName)
    {
        Assert.assertFalse(unknownMetadataName, manager.hasMetadata(unknownMetadataName));
        Assert.assertFalse(unknownMetadataName, manager.getAllMetadataNames().contains(unknownMetadataName));

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> manager.computeMetadataClosure(unknownMetadataName));
        Assert.assertEquals("Unknown metadata: \"" + unknownMetadataName + "\"", e.getMessage());
    }

    private DistributedBinaryMetadata newMetadata(String name)
    {
        return () -> name;
    }

    private DistributedBinaryMetadata newMetadata(String name, String... dependencies)
    {
        return newMetadata(name, Arrays.asList(dependencies));
    }

    private DistributedBinaryMetadata newMetadata(String name, Collection<String> dependencies)
    {
        return new DistributedBinaryMetadata()
        {
            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public Collection<String> getDependencies()
            {
                return dependencies;
            }
        };
    }
}
