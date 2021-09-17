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

package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.block.factory.Functions;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.IdBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.type.MetadataJavaPaths;
import org.finos.legend.pure.runtime.java.compiled.serialization.GraphSerializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdate;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarOutputStream;

public class DistributedBinaryGraphSerializer
{
    private static final int MAX_BIN_FILE_BYTES = 512 * 1024;

    private final DistributedBinaryMetadata metadataDefinition;
    private final Iterable<? extends CoreInstance> nodes;
    private final ProcessorSupport processorSupport;
    private final IdBuilder idBuilder;
    private final GraphSerializer.ClassifierCaches classifierCaches;
    private final MultiDistributedBinaryGraphDeserializer alreadySerialized;

    private DistributedBinaryGraphSerializer(DistributedBinaryMetadata metadataDefinition, Iterable<? extends CoreInstance> nodes, ProcessorSupport processorSupport, MultiDistributedBinaryGraphDeserializer alreadySerialized)
    {
        this.metadataDefinition = metadataDefinition;
        this.nodes = nodes;
        this.processorSupport = processorSupport;
        this.idBuilder = IdBuilder.newIdBuilder(DistributedMetadataHelper.getMetadataIdPrefix(getMetadataName()), this.processorSupport);
        this.classifierCaches = new GraphSerializer.ClassifierCaches(this.processorSupport);
        this.alreadySerialized = alreadySerialized;
    }

    public void serializeToDirectory(Path directory)
    {
        serialize(FileWriters.fromDirectory(directory));
    }

    public void serializeToJar(JarOutputStream stream)
    {
        serialize(FileWriters.fromJarOutputStream(stream));
    }

    public void serializeToInMemoryByteArrays(Map<String, ? super byte[]> fileBytes)
    {
        serialize(FileWriters.fromInMemoryByteArrayMap(fileBytes));
    }

    public void serialize(FileWriter fileWriter)
    {
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = getNodesByClassifierId();

        // Build string cache
        DistributedStringCache stringCache = DistributedStringCache.fromNodes(nodesByClassifierId.valuesView().flatCollect(Functions.identity()), this.idBuilder, this.processorSupport);
        BinaryObjSerializer serializer = new BinaryObjSerializerWithStringCacheAndImplicitIdentifiers(stringCache);

        // Write string cache
        stringCache.write(getMetadataName(), fileWriter);

        // Write instances
        int partition = 0;
        int partitionTotalBytes = 0;
        ByteArrayOutputStream binByteStream = new ByteArrayOutputStream(MAX_BIN_FILE_BYTES);
        try (Writer binFileWriter = BinaryWriters.newBinaryWriter(binByteStream))
        {
            ByteArrayOutputStream indexByteStream = new ByteArrayOutputStream();
            try (Writer indexWriter = BinaryWriters.newBinaryWriter(indexByteStream))
            {
                for (String classifierId : nodesByClassifierId.keysView().toSortedList())
                {
                    MutableList<ObjOrUpdate> classifierObjs = buildObjOrUpdates(classifierId, nodesByClassifierId.get(classifierId)).sortThisBy(ObjOrUpdate::getIdentifier);

                    // Initial index information
                    indexWriter.writeInt(classifierObjs.size()); // total obj count
                    indexWriter.writeInt(partition); // initial partition
                    indexWriter.writeInt(partitionTotalBytes); // initial byte offset in partition

                    MutableList<ObjIndexInfo> partitionObjIndexInfos = Lists.mutable.empty();
                    ByteArrayOutputStream objByteStream = new ByteArrayOutputStream();
                    try (Writer objWriter = BinaryWriters.newBinaryWriter(objByteStream))
                    {
                        for (ObjOrUpdate obj : classifierObjs)
                        {
                            // Obj serialization
                            objByteStream.reset();
                            serializer.serializeObjOrUpdate(objWriter, obj);
                            int objByteCount = objByteStream.size();
                            if (partitionTotalBytes + objByteCount > MAX_BIN_FILE_BYTES)
                            {
                                // Write current partition
                                try (Writer partitionWriter = fileWriter.getWriter(DistributedMetadataHelper.getMetadataPartitionBinFilePath(getMetadataName(), partition)))
                                {
                                    partitionWriter.writeBytes(binByteStream.toByteArray());
                                    binByteStream.reset();
                                }

                                // Write partition portion of classifier index
                                indexWriter.writeInt(partitionObjIndexInfos.size());
                                partitionObjIndexInfos.forEach(info -> info.write(indexWriter, stringCache));

                                // New partition
                                partition++;
                                if (partition < 0)
                                {
                                    throw new RuntimeException("Too many partitions");
                                }
                                partitionTotalBytes = 0;
                                partitionObjIndexInfos.clear();
                            }
                            binFileWriter.writeBytes(objByteStream.toByteArray());
                            partitionTotalBytes += objByteCount;
                            partitionObjIndexInfos.add(new ObjIndexInfo(obj.getIdentifier(), objByteCount));
                        }
                    }

                    // Write final partition portion of classifier index
                    if (partitionObjIndexInfos.notEmpty())
                    {
                        indexWriter.writeInt(partitionObjIndexInfos.size());
                        partitionObjIndexInfos.forEach(info -> info.write(indexWriter, stringCache));
                    }

                    // Write classifier index
                    try (Writer indexFileWriter = fileWriter.getWriter(DistributedMetadataHelper.getMetadataClassifierIndexFilePath(getMetadataName(), classifierId)))
                    {
                        indexFileWriter.writeBytes(indexByteStream.toByteArray());
                        indexByteStream.reset();
                    }
                }
            }
        }

        // Write final partition
        if (binByteStream.size() > 0)
        {
            try (Writer writer = fileWriter.getWriter(DistributedMetadataHelper.getMetadataPartitionBinFilePath(getMetadataName(), partition)))
            {
                writer.writeBytes(binByteStream.toByteArray());
            }
        }

        // Possibly write metadata definition
        if (this.metadataDefinition != null)
        {
            this.metadataDefinition.writeMetadataDefinition(fileWriter);
        }
    }

    private String getMetadataName()
    {
        return (this.metadataDefinition == null) ? null : this.metadataDefinition.getName();
    }

    private Obj buildObj(CoreInstance instance)
    {
        return GraphSerializer.buildObj(instance, this.idBuilder, this.classifierCaches, this.processorSupport);
    }

    private MutableList<ObjOrUpdate> buildObjOrUpdates(String classifierId, MutableList<CoreInstance> instances)
    {
        MutableList<ObjOrUpdate> result = instances.collect(this::buildObj);
        if (this.alreadySerialized != null)
        {
            MapIterable<String, Obj> existingObjs = this.alreadySerialized.getInstancesIfPresent(classifierId, result.collect(ObjOrUpdate::getIdentifier, Sets.mutable.ofInitialCapacity(result.size()))).groupByUniqueKey(Obj::getIdentifier);
            if (existingObjs.notEmpty())
            {
                ListIterator<ObjOrUpdate> listIterator = result.listIterator();
                while (listIterator.hasNext())
                {
                    ObjOrUpdate obj = listIterator.next();
                    Obj existingObj = existingObjs.get(obj.getIdentifier());
                    if (existingObj != null)
                    {
                        listIterator.set(existingObj.computeUpdate((Obj) obj));
                    }
                }
                result.removeIf(Objects::isNull);
            }
        }
        return result;
    }

    private MutableMap<String, MutableList<CoreInstance>> getNodesByClassifierId()
    {
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = Maps.mutable.empty();
        MutableMap<CoreInstance, String> classifierIdCache = Maps.mutable.empty();
        MutableSet<CoreInstance> excludedTypes = PrimitiveUtilities.getPrimitiveTypes(this.processorSupport).toSet();
        AnyStubHelper.getStubClasses().collect(this.processorSupport::package_getByUserPath, excludedTypes);
        this.nodes.forEach(node ->
        {
            CoreInstance classifier = node.getClassifier();
            if (!excludedTypes.contains(classifier))
            {
                String classifierId = classifierIdCache.getIfAbsentPutWithKey(classifier, MetadataJavaPaths::buildMetadataKeyFromType);
                nodesByClassifierId.getIfAbsentPut(classifierId, Lists.mutable::empty).add(node);
            }
        });
        return nodesByClassifierId;
    }

    public static DistributedBinaryGraphSerializer newSerializer(Iterable<? extends CoreInstance> nodes, ProcessorSupport processorSupport)
    {
        Objects.requireNonNull(nodes, "nodes may not be null");
        Objects.requireNonNull(processorSupport, "processorSupport may not be null");
        return new DistributedBinaryGraphSerializer(null, nodes, processorSupport, null);
    }

    public static DistributedBinaryGraphSerializer newSerializer(DistributedBinaryMetadata metadataDefinition, Iterable<? extends CoreInstance> nodes, ProcessorSupport processorSupport)
    {
        Objects.requireNonNull(metadataDefinition, "metadataDefinition may not be null");
        Objects.requireNonNull(nodes, "nodes may not be null");
        Objects.requireNonNull(processorSupport, "processorSupport may not be null");
        Set<String> dependencies = metadataDefinition.getDependencies();
        if (!dependencies.isEmpty())
        {
            throw new IllegalArgumentException(Lists.mutable.withAll(dependencies).sortThis().makeString("Missing metadata dependencies: ", ", ", ""));
        }
        return new DistributedBinaryGraphSerializer(metadataDefinition, nodes, processorSupport, null);
    }

    public static DistributedBinaryGraphSerializer newSerializer(DistributedBinaryMetadata metadataDefinition, Iterable<? extends CoreInstance> nodes, ProcessorSupport processorSupport, ClassLoader classLoader)
    {
        return newSerializer(metadataDefinition, nodes, processorSupport, FileReaders.fromClassLoader(classLoader));
    }

    public static DistributedBinaryGraphSerializer newSerializer(DistributedBinaryMetadata metadataDefinition, Iterable<? extends CoreInstance> nodes, ProcessorSupport processorSupport, FileReader fileReader)
    {
        Objects.requireNonNull(metadataDefinition, "metadataDefinition may not be null");
        Objects.requireNonNull(nodes, "nodes may not be null");
        Objects.requireNonNull(processorSupport, "processorSupport may not be null");
        Objects.requireNonNull(fileReader, "fileReader may not be null");
        Set<String> dependencies = metadataDefinition.getDependencies();
        MultiDistributedBinaryGraphDeserializer alreadySerialized = dependencies.isEmpty() ? null : MultiDistributedBinaryGraphDeserializer.fromFileReader(fileReader, dependencies);
        return new DistributedBinaryGraphSerializer(metadataDefinition, nodes, processorSupport, alreadySerialized);
    }

    public static DistributedBinaryGraphSerializer newSerializer(PureRuntime runtime)
    {
        Objects.requireNonNull(runtime, "runtime may not be null");
        return newSerializer(getRuntimeAllNodesIterable(runtime), runtime.getProcessorSupport());
    }

    public static DistributedBinaryGraphSerializer newSerializer(DistributedBinaryMetadata metadataDefinition, PureRuntime runtime)
    {
        Objects.requireNonNull(runtime, "runtime may not be null");
        return newSerializer(metadataDefinition, getRuntimeAllNodesIterable(runtime), runtime.getProcessorSupport());
    }

    public static DistributedBinaryGraphSerializer newSerializer(DistributedBinaryMetadata metadataDefinition, PureRuntime runtime, ClassLoader classLoader)
    {
        Objects.requireNonNull(runtime, "runtime may not be null");
        return newSerializer(metadataDefinition, getRuntimeAllNodesIterable(runtime), runtime.getProcessorSupport(), classLoader);
    }

    public static DistributedBinaryGraphSerializer newSerializer(DistributedBinaryMetadata metadataDefinition, PureRuntime runtime, FileReader fileReader)
    {
        Objects.requireNonNull(runtime, "runtime may not be null");
        return newSerializer(metadataDefinition, getRuntimeAllNodesIterable(runtime), runtime.getProcessorSupport(), fileReader);
    }

    public static DistributedBinaryGraphSerializer newSerializer(String metadataName, PureRuntime runtime)
    {
        return (metadataName == null) ? newSerializer(runtime) : newSerializer(DistributedBinaryMetadata.newMetadata(metadataName), runtime);
    }

    public static void serialize(PureRuntime runtime, Path directory)
    {
        newSerializer(runtime).serializeToDirectory(directory);
    }

    private static Iterable<CoreInstance> getRuntimeAllNodesIterable(PureRuntime runtime)
    {
        return GraphNodeIterable.fromModelRepository(runtime.getModelRepository());
    }

    private static class ObjIndexInfo
    {
        private final String identifier;
        private final int size;

        private ObjIndexInfo(String identifier, int size)
        {
            this.identifier = identifier;
            this.size = size;
        }

        private void write(Writer writer, StringCache stringCache)
        {
            writer.writeInt(stringCache.getStringId(this.identifier));
            writer.writeInt(this.size);
        }
    }
}
