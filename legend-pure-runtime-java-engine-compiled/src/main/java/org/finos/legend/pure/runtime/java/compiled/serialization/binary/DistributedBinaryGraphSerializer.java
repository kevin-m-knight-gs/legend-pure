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
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.ListMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.imports.Imports;
import org.finos.legend.pure.m3.serialization.filesystem.PureCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.repository.CodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.MutableCodeStorage;
import org.finos.legend.pure.m3.serialization.grammar.Parser;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m3.serialization.runtime.Source;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.m4.tools.GraphNodeIterable.NodeFilterResult;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.IdBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.type.MetadataJavaPaths;
import org.finos.legend.pure.runtime.java.compiled.serialization.GraphSerializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjRef;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Primitive;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValue;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueMany;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueOne;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Comparator;
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
        MutableMap<String, MutableList<CoreInstance>> nodesByClassifierId = Maps.mutable.empty();
        MutableMap<CoreInstance, String> classifierIdCache = Maps.mutable.empty();
        getInstancesForSerialization().forEach(instance ->
        {
            String classifierId = classifierIdCache.getIfAbsentPutWithKey(instance.getClassifier(), MetadataJavaPaths::buildMetadataKeyFromType);
            nodesByClassifierId.getIfAbsentPut(classifierId, Lists.mutable::empty).add(instance);
        });
        return nodesByClassifierId;
    }

    private Iterable<? extends CoreInstance> getInstancesForSerialization()
    {
        MutableSet<CoreInstance> stubClassifiers = AnyStubHelper.getStubClasses().collect(this.processorSupport::package_getByUserPath, Sets.mutable.empty());
        MutableSet<CoreInstance> primitiveTypes = PrimitiveUtilities.getPrimitiveTypes(this.processorSupport).toSet();
        if (this.metadataSpecification == null)
        {
            return GraphNodeIterable.fromModelRepository(this.runtime.getModelRepository(), instance ->
            {
                CoreInstance classifier = instance.getClassifier();
                if (stubClassifiers.contains(classifier))
                {
                    return NodeFilterResult.REJECT_AND_CONTINUE;
                }
                if (primitiveTypes.contains(classifier))
                {
                    return NodeFilterResult.REJECT_AND_STOP;
                }
                return NodeFilterResult.ACCEPT_AND_CONTINUE;
            });
        }

        CoreInstance packageClassifier = this.processorSupport.package_getByUserPath(M3Paths.Package);
        String repository = this.metadataSpecification.getName();
        return this.runtime.getSourceRegistry().getSources().asLazy()
                .select(source -> PureCodeStorage.isSourceInRepository(source.getId(), repository))
                .flatCollect(source -> GraphNodeIterable.fromNodes(getSourceElements(source), instance ->
                {
                    CoreInstance classifier = instance.getClassifier();
                    if (packageClassifier == classifier)
                    {
                        return isFromSource(instance, source) ? NodeFilterResult.ACCEPT_AND_CONTINUE : NodeFilterResult.REJECT_AND_STOP;
                    }
                    if (stubClassifiers.contains(classifier))
                    {
                        return NodeFilterResult.REJECT_AND_CONTINUE;
                    }
                    if (primitiveTypes.contains(classifier) || isFromDifferentSource(instance, source))
                    {
                        return NodeFilterResult.REJECT_AND_STOP;
                    }
                    return NodeFilterResult.ACCEPT_AND_CONTINUE;
                }));
    }

    private ListIterable<? extends CoreInstance> getSourceElements(Source source)
    {
        ListIterable<? extends CoreInstance> importGroups = Imports.getImportGroupsForSource(source.getId(), this.processorSupport);
        ListMultimap<Parser, CoreInstance> elementsByParser = source.getElementsByParser();
        if (elementsByParser == null)
        {
            return importGroups;
        }
        return Lists.mutable.<CoreInstance>ofInitialCapacity(elementsByParser.size() + importGroups.size())
                .withAll(elementsByParser.valuesView())
                .withAll(importGroups);
    }

    private MapIterable<String, MutableList<Obj>> getObjUpdatesByClassifierId()
    {
        if (this.metadataSpecification == null)
        {
            return Maps.immutable.empty();
        }

        MutableList<Obj> packageUpdates = computePackageUpdates();
        // TODO
        return packageUpdates.isEmpty() ? Maps.immutable.empty() : Maps.immutable.with(M3Paths.Package, packageUpdates);
    }

    private MutableList<Obj> computePackageUpdates()
    {
        if (this.metadataSpecification == null)
        {
            return Lists.mutable.empty();
        }

        class PackageUpdate
        {
            private final MutableSet<ObjRef> children = Sets.mutable.empty();
            private final MutableList<ObjRef> refUsages = Lists.mutable.empty();

            PackageUpdate withChild(ObjRef child)
            {
                this.children.add(child);
                return this;
            }

            boolean notEmpty()
            {
                return this.children.notEmpty() || this.refUsages.notEmpty();
            }
        }

        // Collect package children and reference usages for this repository
        String repository = this.metadataSpecification.getName();
        MutableMap<CoreInstance, PackageUpdate> packageUpdates = Maps.mutable.empty();
        PackageTreeIterable.newRootPackageTreeIterable(this.runtime.getModelRepository()).forEach(pkg ->
        {
            PackageUpdate update = new PackageUpdate();
            pkg._children().collectIf(
                    i -> isInRepository(i, repository),
                    i -> new ObjRef(this.classifierCaches.getClassifierId(i.getClassifier()), this.idBuilder.buildId(i)),
                    update.children);
            pkg._referenceUsages().collectIf(
                    r -> isInRepository(AnyStubHelper.fromStub(r._ownerCoreInstance()), repository),
                    ru -> new ObjRef(M3Paths.ReferenceUsage, this.idBuilder.buildId(ru)),
                    update.refUsages);
            if (update.notEmpty())
            {
                packageUpdates.put(pkg, update);
            }
        });

        if (packageUpdates.isEmpty())
        {
            return Lists.mutable.empty();
        }

        // Populate parent packages
        Lists.mutable.withAll(packageUpdates.keySet()).forEach(pkg ->
        {
            CoreInstance child = pkg;
            CoreInstance parent;
            while ((child != null) && (parent = child.getValueForMetaPropertyToOne(M3Properties._package)) != null)
            {
                String classifierId = this.classifierCaches.getClassifierId(child.getClassifier());
                String id = this.idBuilder.buildId(child);
                ObjRef objRef = new ObjRef(classifierId, id);
                if (packageUpdates.containsKey(parent))
                {
                    packageUpdates.get(parent).withChild(objRef);
                    child = null;
                }
                else
                {
                    packageUpdates.put(parent, new PackageUpdate().withChild(objRef));
                    child = parent;
                }
            }
        });

        // Compute updates
        MutableList<Obj> updates = Lists.mutable.withInitialCapacity(packageUpdates.size());
        Comparator<ObjRef> objRefComparator = Comparator.comparing(ObjRef::getClassifierId).thenComparing(ObjRef::getId);
        packageUpdates.forEachKeyValue((pkg, update) ->
        {
            MutableList<PropertyValue> propertyValues = Lists.mutable.empty();

            // name
            propertyValues.add(new PropertyValueOne(M3Properties.name, new Primitive(pkg.getName())));

            // package
            CoreInstance parent = pkg.getValueForMetaPropertyToOne(M3Properties._package);
            if (parent != null)
            {
                propertyValues.add(new PropertyValueOne(M3Properties._package, new ObjRef(M3Paths.Package, this.idBuilder.buildId(parent))));
            }

            // children
            if (update.children.notEmpty())
            {
                propertyValues.add((update.children.size() == 1) ? new PropertyValueOne(M3Properties.children, update.children.getAny()) : new PropertyValueMany(M3Properties.children, Lists.immutable.withAll(update.children.toSortedList(objRefComparator))));
            }

            // referenceUsages
            if (update.refUsages.notEmpty())
            {
                propertyValues.add((update.refUsages.size() == 1) ? new PropertyValueOne(M3Properties.referenceUsages, update.refUsages.getAny()) : new PropertyValueMany(M3Properties.referenceUsages, Lists.immutable.withAll(update.refUsages.toSortedList(objRefComparator))));
            }

            Obj obj = Obj.newObj(M3Paths.Package, this.idBuilder.buildId(pkg), pkg.getName(), propertyValues.toImmutable(), null, false);
            updates.add(obj);
        });
        return updates;
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

    @Deprecated
    public static DistributedBinaryGraphSerializer newSerializer(String metadataName, PureRuntime runtime)
    {
        if (metadataName == null)
        {
            return newSerializer(runtime);
        }
        throw new UnsupportedOperationException();
    }

    public static DistributedBinaryGraphSerializer newSerializer(PureRuntime runtime)
    {
        return new DistributedBinaryGraphSerializer(null, runtime);
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
        MutableSet<String> directDependencies = allRepositories.collectIf(r -> (r != repository) && repository.isVisible(r), CodeRepository::getName, Sets.mutable.empty());
        DistributedMetadataSpecification metadataSpecification = DistributedMetadataSpecification.newSpecification(repositoryName, directDependencies);
        return new DistributedBinaryGraphSerializer(metadataSpecification, runtime);
    }

    public static void serialize(PureRuntime runtime, Path directory)
    {
        newSerializer(runtime).serializeToDirectory(directory);
    }

    private static boolean isInRepository(CoreInstance instance, String repository)
    {
        SourceInformation sourceInfo = instance.getSourceInformation();
        return (sourceInfo != null) && PureCodeStorage.isSourceInRepository(sourceInfo.getSourceId(), repository);
    }

    private static boolean isFromSource(CoreInstance instance, Source source)
    {
        SourceInformation sourceInfo = instance.getSourceInformation();
        return (sourceInfo != null) && source.getId().equals(sourceInfo.getSourceId());
    }

    private static boolean isFromDifferentSource(CoreInstance instance, Source source)
    {
        SourceInformation sourceInfo = instance.getSourceInformation();
        return (sourceInfo != null) && !source.getId().equals(sourceInfo.getSourceId());
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
