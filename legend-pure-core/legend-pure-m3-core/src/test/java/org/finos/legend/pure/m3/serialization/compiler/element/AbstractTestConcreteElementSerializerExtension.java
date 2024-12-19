// Copyright 2024 Goldman Sachs
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

package org.finos.legend.pure.m3.serialization.compiler.element;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.property.Property;
import org.finos.legend.pure.m3.serialization.compiler.reference.AbstractReferenceTest;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProviders;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolver;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolvers;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.binary.BinaryReaders;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.function.Function;

public abstract class AbstractTestConcreteElementSerializerExtension extends AbstractReferenceTest
{
    private ConcreteElementSerializerExtension extension;
    private ConcreteElementSerializer serializer;
    private ConcreteElementDeserializer deserializer;
    private ReferenceIdProviders referenceIdProviders;
    private ReferenceIdResolvers referenceIdResolvers;
    private MapIterable<String, ImmutableList<String>> backRefProperties;

    @Before
    public void setUpExtension()
    {
        this.extension = getExtension();
        this.referenceIdProviders = ReferenceIdProviders.builder().withAvailableExtensions().withProcessorSupport(processorSupport).build();
        this.referenceIdResolvers = ReferenceIdResolvers.builder().withAvailableExtensions().withPackagePathResolver(processorSupport::package_getByUserPath).build();
        this.serializer = ConcreteElementSerializer.builder(processorSupport).withExtension(this.extension).withReferenceIdProviders(this.referenceIdProviders).build();
        this.deserializer = ConcreteElementDeserializer.builder().withExtension(this.extension).build();
        this.backRefProperties = M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.groupByUniqueKey(ImmutableList::getLast);
    }

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
    public void testSerializeDeserializeAll()
    {
        GraphTools.getTopLevelAndPackagedElements(processorSupport)
                .select(c -> c.getSourceInformation() != null)
                .forEach(this::testSerializeDeserialize);
    }

    private void testSerializeDeserialize(CoreInstance element)
    {
        String path = PackageableElement.getUserPathForPackageableElement(element);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try
        {
            this.serializer.serialize(BinaryWriters.newBinaryWriter(stream), element);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error serializing " + path, e);
        }
        DeserializedConcreteElement deserialized;
        try
        {
            deserialized = this.deserializer.deserialize(BinaryReaders.newBinaryReader(stream.toByteArray()));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error deserializing " + path, e);
        }
        Assert.assertEquals(path, deserialized.getPath());
        Assert.assertEquals(path, this.referenceIdProviders.getDefaultVersion(), deserialized.getReferenceIdVersion());
        MutableIntObjectMap<CoreInstance> internalReferences = IntObjectMaps.mutable.<CoreInstance>empty().withKeyValue(0, element);
        assertDeserialization(deserialized, internalReferences, path, element, deserialized.getConcreteElementData());
        Assert.assertEquals(path, internalReferences.keysView().toSortedList(), IntInterval.zeroTo(deserialized.getInstanceData().size() - 1));
    }

    private void assertDeserialization(DeserializedConcreteElement concreteElement, MutableIntObjectMap<CoreInstance> internalReferences, String path, CoreInstance element, InstanceData instanceData)
    {
        ReferenceIdResolver referenceIdResolver = this.referenceIdResolvers.resolver(concreteElement.getReferenceIdVersion());

        if (ModelRepository.isAnonymousInstanceName(element.getName()))
        {
            Assert.assertNull(path, instanceData.getName());
        }
        else
        {
            Assert.assertEquals(path, element.getName(), instanceData.getName());
        }
        Assert.assertEquals(path, element.getSourceInformation(), instanceData.getSourceInformation());
        Assert.assertEquals(path, PackageableElement.getUserPathForPackageableElement(processorSupport.getClassifier(element)), instanceData.getClassifierPath());

        MutableMap<String, PropertyValues> deserializedPropertyValues = Maps.mutable.empty();
        instanceData.getPropertyValues().forEach(pv ->
        {
            if ((deserializedPropertyValues.put(pv.getPropertyName(), pv) != null))
            {
                Assert.fail("Multiple property values for '" + pv.getPropertyName() + "' for " + path);
            }
        });

        MutableList<String> nonEmptyPropertyKeys = Lists.mutable.empty();
        processorSupport.class_getSimpleProperties(processorSupport.getClassifier(element)).forEach(property ->
        {
            String key = property.getName();
            ListIterable<? extends CoreInstance> values = element.getValueForMetaPropertyToMany(key);
            if (values.notEmpty() && (!isBackRefProperty(property) || values.anySatisfy(v -> !backReferenceIsExternal(key, v, concreteElement.getConcreteElementData().getSourceInformation()))))
            {
                nonEmptyPropertyKeys.add(key);
                PropertyValues pValues = deserializedPropertyValues.get(key);
                if (pValues != null)
                {
                    Assert.assertEquals(path + "." + key, PackageableElement.getUserPathForPackageableElement(Property.getPropertySourceType(property, processorSupport)), pValues.getPropertySourceType());
                }
            }
        });
        Assert.assertEquals(path, nonEmptyPropertyKeys.sortThis(), deserializedPropertyValues.keysView().toSortedList());

        nonEmptyPropertyKeys.forEach(key ->
        {
            ListIterable<? extends CoreInstance> values = element.getValueForMetaPropertyToMany(key);
            if (isBackRefProperty(key))
            {
                values = values.reject(ru -> backReferenceIsExternal(key, ru, concreteElement.getConcreteElementData().getSourceInformation()));
            }
            ListIterable<ValueOrReference> pValues = deserializedPropertyValues.get(key).getValues();
            String keyPath = path + "." + key;
            Assert.assertEquals(keyPath, values.size(), pValues.size());
            values.forEachWithIndex((value, i) ->
            {
                String keyPathWithIndex = keyPath + "[" + i + "]";
                pValues.get(i).visit(new ValueOrReferenceConsumer()
                {
                    @Override
                    protected void accept(Reference.ExternalReference reference)
                    {
                        CoreInstance resolved = referenceIdResolver.resolveReference(reference.getId());
                        Assert.assertSame(keyPathWithIndex + "=" + reference.getId(), value, resolved);
                    }

                    @Override
                    protected void accept(Reference.InternalReference reference)
                    {
                        int internalId = reference.getId();
                        CoreInstance visited = internalReferences.get(internalId);
                        if (visited == null)
                        {
                            InstanceData resolved = concreteElement.getInstanceData(internalId);
                            Assert.assertNotNull(keyPathWithIndex + "=" + internalId, resolved);
                            internalReferences.put(internalId, value);
                            assertDeserialization(concreteElement, internalReferences, keyPathWithIndex, value, resolved);
                        }
                        else
                        {
                            Assert.assertSame(keyPathWithIndex + "=" + internalId, value, visited);
                        }
                    }

                    @Override
                    protected void accept(Value.BooleanValue bValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getBooleanValue, bValue);
                    }

                    @Override
                    protected void accept(Value.ByteValue bValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getByteValue, bValue);
                    }

                    @Override
                    protected void accept(Value.DateValue dValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getDateValue, dValue);
                    }

                    @Override
                    protected void accept(Value.DateTimeValue dtValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getDateValue, dtValue);
                    }

                    @Override
                    protected void accept(Value.StrictDateValue sdValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getDateValue, sdValue);
                    }

                    @Override
                    protected void accept(Value.LatestDateValue ldValue)
                    {
                        assertValue(keyPathWithIndex, value, v -> null, ldValue);
                    }

                    @Override
                    protected void accept(Value.DecimalValue dValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getDecimalValue, dValue);
                    }

                    @Override
                    protected void accept(Value.FloatValue fValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getFloatValue, fValue);
                    }

                    @Override
                    protected void accept(Value.IntegerValue iValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getIntegerValue, iValue);
                    }

                    @Override
                    protected void accept(Value.StrictTimeValue stValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getStrictTimeValue, stValue);
                    }

                    @Override
                    protected void accept(Value.StringValue sValue)
                    {
                        assertValue(keyPathWithIndex, value, PrimitiveUtilities::getStringValue, sValue);
                    }
                });
            });
        });
    }

    private void assertValue(String message, CoreInstance expectedInstance, Function<CoreInstance, ?> valueExtractor, Value<?> actualValue)
    {
        Assert.assertEquals(message, PackageableElement.getUserPathForPackageableElement(processorSupport.getClassifier(expectedInstance)), actualValue.getClassifierPath());
        Assert.assertEquals(message, valueExtractor.apply(expectedInstance), actualValue.getValue());
    }

    private boolean isBackRefProperty(String propertyName)
    {
        return this.backRefProperties.containsKey(propertyName);
    }

    private boolean isBackRefProperty(CoreInstance property)
    {
        ImmutableList<String> backRefPropertyPath = this.backRefProperties.get(property.getName());
        return (backRefPropertyPath != null) && backRefPropertyPath.equals(Property.calculatePropertyPath(property, processorSupport));
    }

    private boolean backReferenceIsExternal(String propertyName, CoreInstance value, SourceInformation elementSourceInfo)
    {
        SourceInformation valueSourceInfo = M3Properties.referenceUsages.equals(propertyName) ?
                                            value.getValueForMetaPropertyToOne(M3Properties.owner).getSourceInformation() :
                                            value.getSourceInformation();
        return (valueSourceInfo != null) && !elementSourceInfo.subsumes(valueSourceInfo);
    }

    protected abstract ConcreteElementSerializerExtension getExtension();
}
