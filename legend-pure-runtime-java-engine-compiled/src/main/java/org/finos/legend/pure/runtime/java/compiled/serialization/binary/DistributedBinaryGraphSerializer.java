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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.filesystem.PureCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.repository.CodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.MutableCodeStorage;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
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

    private final DistributedMetadataSpecification metadataSpecification;
    private final PureRuntime runtime;
    private final ProcessorSupport processorSupport;
    private final IdBuilder idBuilder;
    private final GraphSerializer.ClassifierCaches classifierCaches;

    private DistributedBinaryGraphSerializer(DistributedMetadataSpecification metadataSpecification, PureRuntime runtime)
    {
        this.metadataSpecification = metadataSpecification;
        this.runtime = runtime;
        this.processorSupport = runtime.getProcessorSupport();
        this.idBuilder = IdBuilder.newIdBuilder(DistributedMetadataHelper.getMetadataIdPrefix(getMetadataName()), this.processorSupport);
        this.classifierCaches = new GraphSerializer.ClassifierCaches(this.processorSupport);
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
        // Possibly write metadata specification
        if (this.metadataSpecification != null)
        {
            this.metadataSpecification.writeSpecification(fileWriter);
        }

        // Compute instances for serialization
        MapIterable<String, MutableList<CoreInstance>> instancesForSerialization = getInstancesForSerializationByClassifierId();
        MapIterable<String, MutableList<Obj>> objUpdates = getObjUpdatesByClassifierId();

        // Build string cache
        DistributedStringCache stringCache = buildStringCache(instancesForSerialization, objUpdates);
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
                for (String classifierId : stringCache.getClassifierIds().toSortedList())
                {
                    ListIterable<Obj> classifierObjs = getClassifierObjs(classifierId, instancesForSerialization, objUpdates);

                    // Initial index information
                    indexWriter.writeInt(classifierObjs.size()); // total obj count
                    indexWriter.writeInt(partition); // initial partition
                    indexWriter.writeInt(partitionTotalBytes); // initial byte offset in partition

                    MutableList<ObjIndexInfo> partitionObjIndexInfos = Lists.mutable.empty();
                    ByteArrayOutputStream objByteStream = new ByteArrayOutputStream();
                    try (Writer objWriter = BinaryWriters.newBinaryWriter(objByteStream))
                    {
                        for (Obj obj : classifierObjs)
                        {
                            // Obj serialization
                            objByteStream.reset();
                            serializer.serializeObj(objWriter, obj);
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
                    try (Writer writer1 = fileWriter.getWriter(DistributedMetadataHelper.getMetadataClassifierIndexFilePath(getMetadataName(), classifierId)))
                    {
                        writer1.writeBytes(indexByteStream.toByteArray());
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
    }

    private MapIterable<String, MutableList<CoreInstance>> getInstancesForSerializationByClassifierId()
    {
        return indexNodesByClassifierId(getInstancesForSerialization());
    }

    private Iterable<? extends CoreInstance> getInstancesForSerialization()
    {
        if ((this.metadataSpecification == null) || this.metadataSpecification.getDependencies().isEmpty())
        {
            return GraphNodeIterable.fromModelRepository(this.runtime.getModelRepository());
        }
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    private MapIterable<String, MutableList<Obj>> getObjUpdatesByClassifierId()
    {
        if ((this.metadataSpecification == null) || this.metadataSpecification.getDependencies().isEmpty())
        {
            return Maps.immutable.empty();
        }
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    private DistributedStringCache buildStringCache(MapIterable<String, ? extends Iterable<? extends CoreInstance>> nodesByClassifierId, MapIterable<String, ? extends Iterable<? extends Obj>> objUpdatesByClassifierId)
    {
        StringCache.Builder<DistributedStringCache> stringCacheBuilder = DistributedStringCache.newBuilder();
        nodesByClassifierId.forEachValue(instances -> instances.forEach(i -> stringCacheBuilder.withObj(buildObj(i))));
        objUpdatesByClassifierId.forEachValue(stringCacheBuilder::withObjs);
        return stringCacheBuilder.build();
    }

    private ListIterable<Obj> getClassifierObjs(String classifierId, MapIterable<String, ? extends ListIterable<? extends CoreInstance>> nodesByClassifierId, MapIterable<String, ? extends ListIterable<? extends Obj>> objUpdatesByClassifierId)
    {
        ListIterable<? extends CoreInstance> classifierInstances = nodesByClassifierId.get(classifierId);
        ListIterable<? extends Obj> classifierObjUpdates = objUpdatesByClassifierId.get(classifierId);
        MutableList<Obj> classifierObjs = Lists.mutable.withInitialCapacity(((classifierInstances == null) ? 0 : classifierInstances.size()) + ((classifierObjUpdates == null) ? 0 : classifierObjUpdates.size()));
        if (classifierInstances != null)
        {
            classifierInstances.collect(this::buildObj, classifierObjs);
        }
        if (classifierObjUpdates != null)
        {
            classifierObjs.addAllIterable(classifierObjUpdates);
        }
        classifierObjs.sortThisBy(Obj::getIdentifier);
        return classifierObjs;
    }

    private Obj buildObj(CoreInstance instance)
    {
        return GraphSerializer.buildObj(instance, this.idBuilder, this.classifierCaches, this.processorSupport);
    }

    private String getMetadataName()
    {
        return (this.metadataSpecification == null) ? null : this.metadataSpecification.getName();
    }

    private MapIterable<String, MutableList<CoreInstance>> indexNodesByClassifierId(Iterable<? extends CoreInstance> nodes)
    {
        if (nodes == null)
        {
            return Maps.immutable.empty();
        }
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = Maps.mutable.empty();
        MutableMap<CoreInstance, String> classifierIdCache = Maps.mutable.empty();
        MutableSet<CoreInstance> excludedTypes = PrimitiveUtilities.getPrimitiveTypes(this.processorSupport).toSet();
        AnyStubHelper.getStubClasses().collect(this.processorSupport::package_getByUserPath, excludedTypes);
        nodes.forEach(node ->
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

    public static DistributedBinaryGraphSerializer newSerializer(String metadataName, PureRuntime runtime)
    {
        DistributedMetadataSpecification metadataSpecification = (metadataName == null) ? null : DistributedMetadataSpecification.newSpecification(metadataName);
        return new DistributedBinaryGraphSerializer(metadataSpecification, runtime);
    }

    public static DistributedBinaryGraphSerializer newSerializer(PureRuntime runtime)
    {
        return newSerializer(null, runtime);
    }

    public static DistributedBinaryGraphSerializer newSerializer(PureRuntime runtime, String repositoryName)
    {
        MutableCodeStorage codeStorage = runtime.getCodeStorage();
        CodeRepository repository = codeStorage.getRepository(repositoryName);
        if (repository == null)
        {
            throw new IllegalArgumentException("Unknown repository: \"" + repositoryName + "\"");
        }
        RichIterable<CodeRepository> allRepositories = codeStorage.getAllRepositories();
        if (allRepositories.anySatisfy(r -> (r != repository) && r.isVisible(repository)))
        {
            StringBuilder builder = new StringBuilder("Cannot serialize repository \"").append(repositoryName).append(("\", with "));
            allRepositories.select(r -> (r != repository) && r.isVisible(repository)).appendString(builder, "\"", "\", \"", "\" in the runtime");
            throw new IllegalArgumentException(builder.toString());
        }
        MutableSet<String> dependencies = PureCodeStorage.getRepositoryDependenciesByName(allRepositories, repository).without(repositoryName);
        DistributedMetadataSpecification metadataSpecification = DistributedMetadataSpecification.newSpecification(repositoryName, dependencies);
        return new DistributedBinaryGraphSerializer(metadataSpecification, runtime);
    }

    public static void serialize(PureRuntime runtime, Path directory)
    {
        newSerializer(runtime).serializeToDirectory(directory);
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
