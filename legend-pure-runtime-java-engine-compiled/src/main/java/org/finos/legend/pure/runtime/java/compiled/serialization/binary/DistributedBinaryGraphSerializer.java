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
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
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

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarOutputStream;

public class DistributedBinaryGraphSerializer
{
    private static final int MAX_BIN_FILE_BYTES = 512 * 1024;

    protected final String metadataName;
    private final PureRuntime runtime;

    private DistributedBinaryGraphSerializer(String metadataName, PureRuntime runtime)
    {
        this.metadataName = metadataName;
        this.runtime = runtime;
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
        ProcessorSupport processorSupport = this.runtime.getProcessorSupport();
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = getNodesByClassifierId(this.runtime.getModelRepository(), processorSupport);

        // Build string cache
        DistributedStringCache stringCache = DistributedStringCache.fromNodes(nodesByClassifierId.valuesView().flatCollect(Functions.identity()), processorSupport);
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
                    ListIterable<CoreInstance> classifierObjs = nodesByClassifierId.get(classifierId).sortThisBy(coreInstance -> IdBuilder.buildId(coreInstance, processorSupport));

                    // Initial index information
                    indexWriter.writeInt(classifierObjs.size()); // total obj count
                    indexWriter.writeInt(partition); // initial partition
                    indexWriter.writeInt(partitionTotalBytes); // initial byte offset in partition

                    MutableList<ObjIndexInfo> partitionObjIndexInfos = Lists.mutable.empty();
                    ByteArrayOutputStream objByteStream = new ByteArrayOutputStream();
                    try (Writer objWriter = BinaryWriters.newBinaryWriter(objByteStream))
                    {
                        GraphSerializer.ClassifierCaches classifierCaches = new GraphSerializer.ClassifierCaches(processorSupport);
                        for (CoreInstance coreInstance : classifierObjs)
                        {
                            // Obj serialization
                            Obj obj = GraphSerializer.buildObj(coreInstance, classifierCaches, processorSupport);
                            objByteStream.reset();
                            serializer.serializeObj(objWriter, obj);
                            int objByteCount = objByteStream.size();
                            if (partitionTotalBytes + objByteCount > MAX_BIN_FILE_BYTES)
                            {
                                // Write current partition
                                try (Writer partitionWriter = fileWriter.getWriter(DistributedMetadataFiles.getMetadataPartitionBinFilePath(this.metadataName, partition)))
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
                    try (Writer indexFileWriter = fileWriter.getWriter(DistributedMetadataFiles.getMetadataClassifierIndexFilePath(this.metadataName, classifierId)))
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
            try (Writer writer = fileWriter.getWriter(DistributedMetadataFiles.getMetadataPartitionBinFilePath(this.metadataName, partition)))
            {
                writer.writeBytes(binByteStream.toByteArray());
            }
        }
    }

    public static DistributedBinaryGraphSerializer newSerializer(PureRuntime runtime)
    {
        return newSerializer(null, runtime);
    }

    public static DistributedBinaryGraphSerializer newSerializer(String metadataName, PureRuntime runtime)
    {
        return new DistributedBinaryGraphSerializer(metadataName, runtime);
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
