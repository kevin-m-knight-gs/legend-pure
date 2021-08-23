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
import org.eclipse.collections.api.factory.Stacks;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.stack.MutableStack;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.IdBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.type.MetadataJavaPaths;
import org.finos.legend.pure.runtime.java.compiled.serialization.GraphSerializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdate;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarOutputStream;

public class DistributedBinaryGraphSerializer
{
    private static final int MAX_BIN_FILE_BYTES = 512 * 1024;

    private final String metadataName;
    private final PureRuntime runtime;
    private final ProcessorSupport processorSupport;
    private final IdBuilder idBuilder;
    private final GraphSerializer.ClassifierCaches classifierCaches;
    private final MultiDistributedBinaryGraphDeserializer alreadySerialized;

    private DistributedBinaryGraphSerializer(String metadataName, PureRuntime runtime, MultiDistributedBinaryGraphDeserializer alreadySerialized)
    {
        this.metadataName = DistributedMetadataHelper.validateMetadataNameIfPresent(metadataName);
        this.runtime = runtime;
        this.processorSupport = this.runtime.getProcessorSupport();
        this.idBuilder = IdBuilder.newIdBuilder(DistributedMetadataHelper.getMetadataIdPrefix(this.metadataName), this.processorSupport);
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
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = getNodesByClassifierId(this.runtime.getModelRepository(), this.processorSupport);

        // Build string cache
        DistributedStringCache stringCache = DistributedStringCache.fromNodes(nodesByClassifierId.valuesView().flatCollect(Functions.identity()), this.idBuilder, this.processorSupport);
        BinaryObjSerializer serializer = new BinaryObjSerializerWithStringCacheAndImplicitIdentifiers(stringCache);

        // Write string cache
        stringCache.write(this.metadataName, fileWriter);

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
                                try (Writer partitionWriter = fileWriter.getWriter(DistributedMetadataHelper.getMetadataPartitionBinFilePath(this.metadataName, partition)))
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
                    try (Writer indexFileWriter = fileWriter.getWriter(DistributedMetadataHelper.getMetadataClassifierIndexFilePath(this.metadataName, classifierId)))
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
            try (Writer writer = fileWriter.getWriter(DistributedMetadataHelper.getMetadataPartitionBinFilePath(this.metadataName, partition)))
            {
                writer.writeBytes(binByteStream.toByteArray());
            }
        }
    }

    private String buildId(CoreInstance instance)
    {
        return this.idBuilder.buildId(instance);
    }

    private Obj buildObj(CoreInstance instance)
    {
        return GraphSerializer.buildObj(instance, this.idBuilder, this.classifierCaches, this.processorSupport);
    }

    private MutableList<ObjOrUpdate> buildObjOrUpdates(String classifierId, MutableList<CoreInstance> instances)
    {
        if (this.alreadySerialized != null)
        {
            MapIterable<String, Obj> existingObjs = this.alreadySerialized.getInstancesIfPresent(classifierId, instances.asLazy().collect(this::buildId)).groupByUniqueKey(Obj::getIdentifier);
            if (existingObjs.notEmpty())
            {
                return instances.asLazy()
                        .collect(instance ->
                        {
                            Obj obj = buildObj(instance);
                            Obj existingObj = existingObjs.get(obj.getIdentifier());
                            return (existingObj == null) ? obj : existingObj.computeUpdate(obj);
                        })
                        .select(Objects::nonNull, Lists.mutable.empty());
            }
        }
        return instances.collect(this::buildObj);
    }

    public static DistributedBinaryGraphSerializer newSerializer(PureRuntime runtime)
    {
        return newSerializer(null, runtime);
    }

    public static DistributedBinaryGraphSerializer newSerializer(String metadataName, PureRuntime runtime)
    {
        return new DistributedBinaryGraphSerializer(metadataName, runtime, null);
    }

    public static DistributedBinaryGraphSerializer newSerializer(String metadataName, PureRuntime runtime, MultiDistributedBinaryGraphDeserializer alreadySerialized)
    {
        return new DistributedBinaryGraphSerializer(metadataName, runtime, alreadySerialized);
    }

    public static void serialize(PureRuntime runtime, Path directory)
    {
        newSerializer(runtime).serializeToDirectory(directory);
    }

    private static MutableMap<String, MutableList<CoreInstance>> getNodesByClassifierId(ModelRepository repository, ProcessorSupport processorSupport)
    {
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = Maps.mutable.empty();

        MutableMap<CoreInstance, String> classifierIds = Maps.mutable.empty();
        MutableSet<CoreInstance> primitiveTypes = PrimitiveUtilities.getPrimitiveTypes(repository).toSet();
        MutableStack<CoreInstance> stack = Stacks.mutable.withAll(repository.getTopLevels());
        MutableSet<CoreInstance> visited = Sets.mutable.empty();
        while (stack.notEmpty())
        {
            CoreInstance node = stack.pop();
            if (visited.add(node))
            {
                CoreInstance classifier = node.getClassifier();
                String classifierId = classifierIds.getIfAbsentPutWithKey(classifier, c -> MetadataJavaPaths.buildMetadataKeyFromType(c).intern());
                nodesByClassifierId.getIfAbsentPut(classifierId, Lists.mutable::empty).add(node);
                LazyIterate.flatCollect(node.getKeys(), key -> Instance.getValueForMetaPropertyToManyResolved(node, key, processorSupport))
                        .select(v -> !primitiveTypes.contains(v.getClassifier()))
                        .forEach(stack::push);
            }
        }
        return nodesByClassifierId;
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
