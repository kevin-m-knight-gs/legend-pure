// Copyright 2025 Goldman Sachs
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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.finos.legend.pure.m4.serialization.binary.BinaryReaders;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ServiceLoader;

public abstract class AbstractTestModuleMetadataSerializerExtension extends AbstractMetadataTest
{
    private final ModuleMetadataSerializerExtension extension = getExtension();
    private final ModuleMetadataSerializer serializer = ModuleMetadataSerializer.builder().withExtension(this.extension).build();

    @Test
    public void testVersions()
    {
        int expectedVersion = this.extension.version();

        Assert.assertEquals(expectedVersion, this.serializer.getDefaultVersion());
        Assert.assertTrue(this.serializer.isVersionAvailable(expectedVersion));

        MutableIntList versions = IntLists.mutable.empty();
        this.serializer.forEachVersion(versions::add);
        Assert.assertEquals(IntLists.mutable.with(expectedVersion), versions);
    }

    @Test
    public void testFindWithServiceLoader()
    {
        MutableIntObjectMap<ModuleMetadataSerializerExtension> extensions = IntObjectMaps.mutable.empty();
        ServiceLoader.load(ModuleMetadataSerializerExtension.class).forEach(ext ->
        {
            if (extensions.put(ext.version(), ext) != null)
            {
                Assert.fail("Multiple extensions for version: " + ext.version());
            }
        });
        ModuleMetadataSerializerExtension foundExtension = extensions.get(this.extension.version());
        Assert.assertNotNull("Could not find version " + this.extension.version(), foundExtension);
        Assert.assertSame(this.extension.getClass(), foundExtension.getClass());
    }

    @Test
    public void testEmptyModule()
    {
        testModuleMetadataSerializes(new ModuleMetadata("empty_module"));
    }

    @Test
    public void testSimpleModuleWithOneSource()
    {
        testModuleMetadataSerializes(new ModuleMetadata(
                "simple_module",
                newClass("model::classes::MySimpleClass", "/simple_module/model/classes.pure", 1, 1, 5, 1, 1),
                newClass("model::classes::MyOtherClass", "/simple_module/model/classes.pure", 6, 1, 10, 1, 1, newExtRef("model::classes::MySimpleClass", refUsage("model::classes::MyOtherClass.properties['simple].classifierGenericType.typeArguments[1]", "rawType")))
        ));
    }

    @Test
    public void testSimpleModuleWithMultipleSources()
    {
        testModuleMetadataSerializes(new ModuleMetadata(
                "multi_source_module",
                newClass("model::classes::MySimpleClass",
                        "/multi_source_module/model/classes.pure", 1, 1, 5, 1, 1),
                newClass("model::classes::MyOtherClass",
                        "/multi_source_module/model/classes.pure", 6, 1, 10, 1, 1,
                        newExtRef("model::classes::MySimpleClass", refUsage("model::classes::MyOtherClass.properties['simple'].classifierGenericType.typeArguments[1]", "rawType"))),
                newClass("model::classes::MyThirdClass",
                        "/multi_source_module/model/classes.pure", 12, 1, 20, 1, 1,
                        newExtRef("model::classes::MySimpleClass", refUsage("model::classes::MyThirdClass.properties['simple'].classifierGenericType.typeArguments[1]", "rawType"))),
                newAssociation("model::associations::SimpleToOther",
                        "/multi_source_module/model/associations.pure", 2, 1, 7, 1, 1,
                        newExtRef("model::classes::MySimpleClass", propFromAssoc("model::associations::SimpleToOther.properties['toSimple']")),
                        newExtRef("model::classes::MyOtherClass", propFromAssoc("model::associations::SimpleToOther.properties['toOther']"))),
                newAssociation("model::associations::SimpleToThird",
                        "/multi_source_module/model/associations.pure", 9, 1, 16, 1, 1,
                        newExtRef("model::classes::MySimpleClass", propFromAssoc("model::associations::SimpleToThird.properties['toThird']")),
                        newExtRef("model::classes::MyThirdClass", propFromAssoc("model::associations::SimpleToThird.properties['toSimple']"))),
                newAssociation("model::associations::OtherToThird",
                        "/multi_source_module/model/associations.pure", 18, 1, 25, 1, 1,
                        newExtRef("model::classes::MyOtherClass", propFromAssoc("model::associations::OtherToThird.properties['toOther']"), refUsage("model::associations::OtherToThird.properties['toOther'].classifierGenericType.typeArguments[1]", "rawType")),
                        newExtRef("model::classes::MyThirdClass", propFromAssoc("model::associations::OtherToThird.properties['toThird']"), refUsage("model::associations::OtherToThird.properties['toThird'].classifierGenericType.typeArguments[1]", "rawType"))),
                newEnumeration("model::enums::MyFirstEnumeration", "/multi_source_module/model/enums.pure", 3, 1, 6, 1, 1),
                newEnumeration("model::enums::MySecondEnumeration", "/multi_source_module/model/enums.pure", 8, 1, 10, 1, 1)
        ));
    }

    protected abstract ModuleMetadataSerializerExtension getExtension();

    protected void testModuleMetadataSerializes(ModuleMetadata metadata)
    {
        byte[] bytes = toBytes(metadata);
        ModuleMetadata deserialized = fromBytes(bytes);
        Assert.assertEquals(metadata, deserialized);

        byte[] bytes2 = toBytes(metadata);
        Assert.assertArrayEquals("serialization instability for version " + this.extension.version() + " (" + this.extension.getClass().getName() + ")", bytes, bytes2);

        byte[] bytes3 = toBytes(deserialized);
        Assert.assertArrayEquals("serialization instability for version " + this.extension.version() + " (" + this.extension.getClass().getName() + ")", bytes, bytes3);
    }

    protected byte[] toBytes(ModuleMetadata metadata)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        this.serializer.serialize(BinaryWriters.newBinaryWriter(bytes), metadata);
        return bytes.toByteArray();
    }

    protected ModuleMetadata fromBytes(byte[] bytes)
    {
        return this.serializer.deserialize(BinaryReaders.newBinaryReader(bytes));
    }
}
