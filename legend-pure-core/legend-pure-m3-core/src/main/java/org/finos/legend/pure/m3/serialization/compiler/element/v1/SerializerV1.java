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

package org.finos.legend.pure.m3.serialization.compiler.element.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.navigation.property.Property;
import org.finos.legend.pure.m3.serialization.compiler.element.SerializationContext;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.Writer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

class SerializerV1 extends BaseV1
{
    private static final ImmutableMap<String, ImmutableList<String>> SKIP_PROPERTY_PATHS = M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS
            .groupByUniqueKey(ImmutableList::getLast, Maps.mutable.ofInitialCapacity(M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.size()))
            .toImmutable();

    private final CoreInstance element;
    private final SerializationContext serializationContext;
    private final ProcessorSupport processorSupport;
    private final MutableList<NodeToSerialize> nodesToSerialize = Lists.mutable.empty();
    private final MutableMap<CoreInstance, String> classifierPathCache = Maps.mutable.empty();
    private final MutableMap<CoreInstance, ListIterable<PropertyInfo>> propertyInfoCache = Maps.mutable.empty();
    private final MapIterable<CoreInstance, BiConsumer<Writer, CoreInstance>> primitiveSerializers;

    SerializerV1(CoreInstance element, SerializationContext serializationContext)
    {
        this.element = element;
        this.serializationContext = serializationContext;
        this.processorSupport = serializationContext.getProcessorSupport();
        this.primitiveSerializers = Maps.mutable.<CoreInstance, BiConsumer<Writer, CoreInstance>>ofInitialCapacity(11)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.Boolean), SerializerV1::serializeBoolean)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.Byte), SerializerV1::serializeByte)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.Date), SerializerV1::serializeDate)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.DateTime), SerializerV1::serializeDateTime)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.Decimal), SerializerV1::serializeDecimal)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.Float), SerializerV1::serializeFloat)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.Integer), SerializerV1::serializeInteger)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.LatestDate), SerializerV1::serializeLatestDate)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.StrictDate), SerializerV1::serializeStrictDate)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.StrictTime), SerializerV1::serializeStrictTime)
                .withKeyValue(this.processorSupport.package_getByUserPath(M3Paths.String), SerializerV1::serializeString);
    }

    void serialize(Writer writer)
    {
        Writer stringIndexedWriter = collectNodesAndIndexStrings(writer);
        stringIndexedWriter.writeString(PackageableElement.getUserPathForPackageableElement(this.element));
        stringIndexedWriter.writeString(this.element.getSourceInformation().getSourceId());
        serializeNodes(stringIndexedWriter);
    }

    private Writer collectNodesAndIndexStrings(Writer writer)
    {
        MutableSet<String> strings = Sets.mutable.empty();
        MutableSet<CoreInstance> visited = Sets.mutable.empty();
        Deque<NodeToSerialize> deque = new ArrayDeque<>();
        deque.add(toSerialize(this.element));
        strings.add(PackageableElement.getUserPathForPackageableElement(this.element));
        strings.add(this.element.getSourceInformation().getSourceId());
        while (!deque.isEmpty())
        {
            NodeToSerialize node = deque.pollFirst();
            if (visited.add(node.instance))
            {
                this.nodesToSerialize.add(node);
                if (!isAnonymousInstance(node.instance))
                {
                    strings.add(node.instance.getName());
                }
                // We treat the classifier as an external reference, even if it's not
                strings.add(this.serializationContext.getReferenceIdProvider().getReferenceId(node.classifier));
                getPropertyInfos(node.classifier).forEach(propertyInfo ->
                {
                    if (!propertyInfo.skip)
                    {
                        ListIterable<? extends CoreInstance> values = node.instance.getValueForMetaPropertyToMany(propertyInfo.name);
                        if (values.notEmpty())
                        {
                            strings.add(propertyInfo.name);
                            strings.add(propertyInfo.sourceType);
                            values.forEach(value ->
                            {
                                if (isExternal(value))
                                {
                                    strings.add(this.serializationContext.getReferenceIdProvider().getReferenceId(value));
                                }
                                else
                                {
                                    CoreInstance valueClassifier = this.processorSupport.getClassifier(value);
                                    if (!isPrimitiveType(valueClassifier))
                                    {
                                        deque.addLast(toSerialize(value, valueClassifier));
                                    }
                                    else if (M3Paths.String.equals(valueClassifier.getName()))
                                    {
                                        strings.add(PrimitiveUtilities.getStringValue(value));
                                    }
                                }
                            });
                        }
                    }
                });
            }
        }
        return this.serializationContext.getStringIndexer().writeStringIndex(writer, strings);
    }

    private void serializeNodes(Writer writer)
    {
        MutableObjectIntMap<CoreInstance> internalIds = ObjectIntMaps.mutable.ofInitialCapacity(this.nodesToSerialize.size());
        this.nodesToSerialize.forEachWithIndex((node, i) -> internalIds.put(node.instance, i));
        writer.writeInt(this.nodesToSerialize.size());
        int internalIdWidth = getIntWidth(this.nodesToSerialize.size());
        this.nodesToSerialize.forEach(node -> serializeNode(writer, node, internalIds, internalIdWidth));
    }

    private void serializeNode(Writer writer, NodeToSerialize node, ObjectIntMap<CoreInstance> internalIds, int internalIdWidth)
    {
        serializeName(writer, node.instance);
        serializeClassifier(writer, node.classifier);
        serializeSourceInfo(writer, node.instance);

        MutableList<Pair<PropertyInfo, ListIterable<? extends CoreInstance>>> propertiesWithValues = Lists.mutable.empty();
        getPropertyInfos(node.classifier).forEach(propertyInfo ->
        {
            if (!propertyInfo.skip)
            {
                ListIterable<? extends CoreInstance> values = node.instance.getValueForMetaPropertyToMany(propertyInfo.name);
                if (values.notEmpty())
                {
                    propertiesWithValues.add(Tuples.pair(propertyInfo, values));
                }
            }
        });
        writer.writeInt(propertiesWithValues.size());
        propertiesWithValues.forEach(pair ->
        {
            PropertyInfo propertyInfo = pair.getOne();
            ListIterable<? extends CoreInstance> values = pair.getTwo();
            writer.writeString(propertyInfo.name);
            writer.writeString(propertyInfo.sourceType);
            writer.writeInt(values.size());
            values.forEach(value ->
            {
                int internalId = internalIds.getIfAbsent(value, -1);
                if (internalId != -1)
                {
                    serializeInternalReference(writer, internalId, internalIdWidth);
                }
                else if (isExternal(value))
                {
                    serializeExternalReference(writer, this.serializationContext.getReferenceIdProvider().getReferenceId(value));
                }
                else
                {
                    CoreInstance valueClassifier = this.processorSupport.getClassifier(value);
                    BiConsumer<Writer, CoreInstance> serializer = this.primitiveSerializers.get(valueClassifier);
                    if (serializer == null)
                    {
                        StringBuilder builder = new StringBuilder("Expected primitive value, found instance of ");
                        PackageableElement.writeUserPathForPackageableElement(builder, valueClassifier);
                        builder.append(": ").append(value);
                        throw new IllegalStateException(builder.toString());
                    }
                    serializer.accept(writer, value);
                }
            });
        });
    }

    private void serializeClassifier(Writer writer, CoreInstance classifier)
    {
        // We treat the classifier as an external reference, even if it's not
        writer.writeString(this.serializationContext.getReferenceIdProvider().getReferenceId(classifier));
    }

    private void serializeName(Writer writer, CoreInstance instance)
    {
        if (isAnonymousInstance(instance))
        {
            writer.writeBoolean(false);
        }
        else
        {
            writer.writeBoolean(true);
            writer.writeString(instance.getName());
        }
    }

    private void serializeSourceInfo(Writer writer, CoreInstance instance)
    {
        SourceInformation sourceInfo = instance.getSourceInformation();
        if (sourceInfo == null)
        {
            writer.writeByte((byte) VALUE_NOT_PRESENT);
        }
        else
        {
            int intWidth = getIntWidth(sourceInfo.getStartLine(), sourceInfo.getStartColumn(), sourceInfo.getLine(), sourceInfo.getColumn(), sourceInfo.getEndLine(), sourceInfo.getEndColumn());
            writer.writeByte((byte) (VALUE_PRESENT | intWidth));
            writeIntOfWidth(writer, sourceInfo.getStartLine(), intWidth);
            writeIntOfWidth(writer, sourceInfo.getStartColumn(), intWidth);
            writeIntOfWidth(writer, sourceInfo.getLine(), intWidth);
            writeIntOfWidth(writer, sourceInfo.getColumn(), intWidth);
            writeIntOfWidth(writer, sourceInfo.getEndLine(), intWidth);
            writeIntOfWidth(writer, sourceInfo.getEndColumn(), intWidth);
        }
    }

    private void serializeExternalReference(Writer writer, String id)
    {
        writer.writeByte((byte) EXTERNAL_REFERENCE);
        writer.writeString(id);
    }

    private void serializeInternalReference(Writer writer, int id, int idWidth)
    {
        writer.writeByte((byte) INTERNAL_REFERENCE);
        writeIntOfWidth(writer, id, idWidth);
    }

    private NodeToSerialize toSerialize(CoreInstance instance)
    {
        return toSerialize(instance, this.processorSupport.getClassifier(instance));
    }

    private NodeToSerialize toSerialize(CoreInstance instance, CoreInstance classifier)
    {
        return new NodeToSerialize(instance, classifier);
    }

    private String getClassifierPath(CoreInstance classifier)
    {
        return this.classifierPathCache.getIfAbsentPutWithKey(classifier, PackageableElement::getUserPathForPackageableElement);
    }

    private ListIterable<PropertyInfo> getPropertyInfos(CoreInstance classifier)
    {
        return this.propertyInfoCache.getIfAbsentPut(classifier, () ->
        {
            MapIterable<String, CoreInstance> propertiesByName = this.processorSupport.class_getSimplePropertiesByName(classifier);
            MutableList<PropertyInfo> infos = Lists.mutable.ofInitialCapacity(propertiesByName.size());
            propertiesByName.forEachKeyValue((name, property) -> infos.add(computePropertyInfo(name, property)));
            return infos.sortThisBy(pi -> pi.name);
        });
    }

    private PropertyInfo computePropertyInfo(String name, CoreInstance property)
    {
        if (shouldSkipProperty(name, property))
        {
            return new PropertyInfo(name, null, true);
        }
        CoreInstance sourceType = Property.getSourceType(property, this.processorSupport);
        return new PropertyInfo(name, PackageableElement.getUserPathForPackageableElement(sourceType), false);
    }

    private boolean shouldSkipProperty(String propertyName, CoreInstance property)
    {
        ImmutableList<String> realKeyToSkip = SKIP_PROPERTY_PATHS.get(propertyName);
        return (realKeyToSkip != null) && realKeyToSkip.equals(Property.calculatePropertyPath(property, this.processorSupport));
    }

    private boolean isPrimitiveType(CoreInstance classifier)
    {
        return this.primitiveSerializers.containsKey(classifier);
    }

    private boolean isExternal(CoreInstance instance)
    {
        if (this.element == instance)
        {
            return false;
        }

        SourceInformation sourceInfo = instance.getSourceInformation();
        return (sourceInfo == null) ? _Package.isPackage(instance, this.processorSupport) : !this.element.getSourceInformation().subsumes(sourceInfo);
    }

    private boolean isAnonymousInstance(CoreInstance instance)
    {
        return ModelRepository.isAnonymousInstanceName(instance.getName());
    }

    private ProcessorSupport getProcessorSupport()
    {
        return this.serializationContext.getProcessorSupport();
    }

    private static class NodeToSerialize
    {
        private final CoreInstance instance;
        private final CoreInstance classifier;

        private NodeToSerialize(CoreInstance instance, CoreInstance classifier)
        {
            this.instance = instance;
            this.classifier = classifier;
        }
    }

    private static class PropertyInfo
    {
        private final String name;
        private final String sourceType;
        private final boolean skip;

        private PropertyInfo(String name, String sourceType, boolean skip)
        {
            this.name = name;
            this.sourceType = sourceType;
            this.skip = skip;
        }
    }
}
