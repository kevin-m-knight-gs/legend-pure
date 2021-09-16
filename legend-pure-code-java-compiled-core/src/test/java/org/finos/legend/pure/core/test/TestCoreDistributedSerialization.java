package org.finos.legend.pure.core.test;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.code.core.CoreCodeRepositoryProvider;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.filesystem.PureCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.repository.PlatformCodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.classpath.ClassLoaderCodeStorage;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntimeBuilder;
import org.finos.legend.pure.m3.tools.GraphStatistics;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.runtime.java.compiled.execution.CompiledProcessorSupport;
import org.finos.legend.pure.runtime.java.compiled.metadata.MetadataLazy;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.DistributedBinaryGraphDeserializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.DistributedBinaryGraphSerializer;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.DistributedBinaryMetadata;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.DistributedBinaryMetadataManager;
import org.finos.legend.pure.runtime.java.compiled.serialization.binary.FileReaders;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdate;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdateConsumer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjUpdate;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Set;

public class TestCoreDistributedSerialization
{
    @ClassRule
    public static final TemporaryFolder tmpFolder = new TemporaryFolder();

    private static final String platformMetadataName = "platform";
    private static final String coreMetadataName = "core";

    private static Path platformMetadataDir;
    private static Path coreMetadataDir;
    private static URLClassLoader classLoaderWithMetadata;

    private static ObjectIntMap<String> expectedPlatformCountsByClassifierId;
    private static ObjectIntMap<String> expectedCoreCountsByClassifierId;

    private static MetadataStats expectedPlatformStats;
    private static MetadataStats expectedCoreStats;

    @BeforeClass
    public static void setUp() throws Exception
    {
        platformMetadataDir = tmpFolder.newFolder(platformMetadataName).toPath();
        coreMetadataDir = tmpFolder.newFolder(coreMetadataName).toPath();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();;
        buildPlatformMetadata(classLoader);
        buildCoreMetadata(classLoader);
        classLoaderWithMetadata = new URLClassLoader(new URL[]{platformMetadataDir.toUri().toURL(), coreMetadataDir.toUri().toURL()}, classLoader);
    }

    @AfterClass
    public static void cleanUp() throws Exception
    {
        if (classLoaderWithMetadata != null)
        {
            classLoaderWithMetadata.close();
        }
    }

    private static void buildPlatformMetadata(ClassLoader classLoader)
    {
        PureCodeStorage codeStorage = new PureCodeStorage(null, new ClassLoaderCodeStorage(classLoader, PlatformCodeRepository.newPlatformCodeRepository()));
        PureRuntime runtime = new PureRuntimeBuilder(codeStorage).setTransactionalByDefault(false).buildAndInitialize();
        expectedPlatformCountsByClassifierId = countInstancesByClassifierId(runtime);
        expectedPlatformStats = computeMetadataStats(runtime);

        DistributedBinaryGraphSerializer.newSerializer(DistributedBinaryMetadata.newMetadata(platformMetadataName), runtime).serializeToDirectory(platformMetadataDir);
        DistributedBinaryMetadata.newMetadata(platformMetadataName).writeMetadataDefinition(platformMetadataDir);
    }

    private static void buildCoreMetadata(ClassLoader classLoader)
    {
        PureCodeStorage codeStorage = new PureCodeStorage(null, new ClassLoaderCodeStorage(classLoader, PlatformCodeRepository.newPlatformCodeRepository(), new CoreCodeRepositoryProvider().repository()));
        PureRuntime runtime = new PureRuntimeBuilder(codeStorage).setTransactionalByDefault(false).buildAndInitialize();
        expectedCoreCountsByClassifierId = countInstancesByClassifierId(runtime);
        expectedCoreStats = computeMetadataStats(runtime);

        DistributedBinaryGraphSerializer.newSerializer(DistributedBinaryMetadata.newMetadata(coreMetadataName, platformMetadataName), runtime, FileReaders.fromDirectory(platformMetadataDir)).serializeToDirectory(coreMetadataDir);
        DistributedBinaryMetadata.newMetadata(coreMetadataName, platformMetadataName).writeMetadataDefinition(coreMetadataDir);
    }

    @Test
    public void testLoadMetadataDefinitions()
    {
        Assert.assertEquals(
                Sets.mutable.with(DistributedBinaryMetadata.newMetadata(platformMetadataName), DistributedBinaryMetadata.newMetadata(coreMetadataName, platformMetadataName)),
                Sets.mutable.withAll(DistributedBinaryMetadata.loadAllMetadata(classLoaderWithMetadata)));

        Assert.assertEquals(
                Sets.mutable.with(DistributedBinaryMetadata.newMetadata(platformMetadataName)),
                Sets.mutable.withAll(DistributedBinaryMetadata.loadMetadata(classLoaderWithMetadata, platformMetadataName)));

        Assert.assertEquals(
                Sets.mutable.with(DistributedBinaryMetadata.newMetadata(platformMetadataName), DistributedBinaryMetadata.newMetadata(coreMetadataName, platformMetadataName)),
                Sets.mutable.withAll(DistributedBinaryMetadata.loadMetadata(classLoaderWithMetadata, coreMetadataName)));
    }

    @Test
    public void testMetadataManager()
    {
        DistributedBinaryMetadataManager metadataManager = DistributedBinaryMetadataManager.fromClassLoader(classLoaderWithMetadata);
        Assert.assertEquals(Sets.mutable.with(platformMetadataName, coreMetadataName), metadataManager.getAllMetadataNames());
        Assert.assertEquals(Sets.mutable.with(platformMetadataName), metadataManager.computeMetadataClosure(platformMetadataName));
        Assert.assertEquals(Sets.mutable.with(platformMetadataName, coreMetadataName), metadataManager.computeMetadataClosure(coreMetadataName));
    }

    @Test
    public void testPlatformDeserializer()
    {
        DistributedBinaryGraphDeserializer platformDeserializer = DistributedBinaryGraphDeserializer.fromClassLoader(platformMetadataName, classLoaderWithMetadata);
        MutableSet<String> classifiers = platformDeserializer.getClassifiers().toSet();
        Assert.assertEquals(Collections.emptySet(), expectedPlatformCountsByClassifierId.keysView().reject(classifiers::contains));
        Assert.assertEquals(Collections.emptySet(), classifiers.reject(expectedPlatformCountsByClassifierId::containsKey));

        MutableMap<String, ListIterable<String>> updatesByClassifier = Maps.mutable.empty();
        classifiers.forEach(classifier ->
        {
            ListIterable<ObjOrUpdate> instances = platformDeserializer.getInstances(classifier, platformDeserializer.getClassifierInstanceIds(classifier));
            ListIterable<String> updates = instances.collectIf(o -> o instanceof ObjUpdate, ObjOrUpdate::getIdentifier);
            if (updates.notEmpty())
            {
                updatesByClassifier.put(classifier, updates);
            }
        });
        Assert.assertEquals(Collections.emptyMap(), updatesByClassifier);
    }

    @Test
    public void testCoreDeserializer()
    {
        DistributedBinaryGraphDeserializer platformDeserializer = DistributedBinaryGraphDeserializer.fromClassLoader(platformMetadataName, classLoaderWithMetadata);
        DistributedBinaryGraphDeserializer coreDeserializer = DistributedBinaryGraphDeserializer.fromClassLoader(coreMetadataName, classLoaderWithMetadata);
        MutableSet<String> classifiers = coreDeserializer.getClassifiers().toSet();
        Assert.assertEquals(Collections.emptySet(), expectedCoreCountsByClassifierId.keysView().reject(classifiers::contains));
        Assert.assertEquals(Collections.emptySet(), classifiers.reject(expectedCoreCountsByClassifierId::containsKey));
        Assert.assertEquals(Collections.emptySet(), expectedPlatformCountsByClassifierId.keysView().reject(classifiers::contains));
        Assert.assertNotEquals(Collections.emptySet(), classifiers.reject(expectedPlatformCountsByClassifierId::containsKey));

        MutableList<Obj> shouldBeObjUpdate = Lists.mutable.empty();
        MutableList<ObjUpdate> shouldBeObj = Lists.mutable.empty();
        expectedCoreCountsByClassifierId.keysView().forEach(classifierId ->
        {
            Set<String> platformInstanceIds = platformDeserializer.hasClassifier(classifierId) ? Sets.mutable.withAll(platformDeserializer.getClassifierInstanceIds(classifierId)) : Collections.emptySet();
            coreDeserializer.getInstances(classifierId, coreDeserializer.getClassifierInstanceIds(classifierId))
                    .forEach(new ObjOrUpdateConsumer()
                    {
                        @Override
                        protected void accept(Obj obj)
                        {
                            if (platformInstanceIds.contains(obj.getIdentifier()))
                            {
                                shouldBeObjUpdate.add(obj);
                            }
                        }

                        @Override
                        protected void accept(ObjUpdate objUpdate)
                        {
                            if (!platformInstanceIds.contains(objUpdate.getIdentifier()))
                            {
                                shouldBeObj.add(objUpdate);
                            }
                        }
                    });
        });
        if (shouldBeObjUpdate.notEmpty() || shouldBeObj.notEmpty())
        {
            StringBuilder builder = new StringBuilder();
            Comparator<ObjOrUpdate> comparator = Comparator.comparing(ObjOrUpdate::getClassifier).thenComparing(ObjOrUpdate::getIdentifier);
            if (shouldBeObjUpdate.notEmpty())
            {
                shouldBeObjUpdate.sortThis(comparator);
                builder.append("Should be ObjUpdate (").append(shouldBeObjUpdate.size()).append("):");
                shouldBeObjUpdate.forEach(o -> builder.append("\n\t").append(o.getClassifier()).append(" / ").append(o.getIdentifier()));
                builder.append("\n");
            }
            if (shouldBeObj.notEmpty())
            {
                shouldBeObj.sortThis(comparator);
                builder.append("Should be Obj (").append(shouldBeObj.size()).append("):");
                shouldBeObj.forEach(o -> builder.append("\n\t").append(o.getClassifier()).append(" / ").append(o.getIdentifier()));
                builder.append("\n");
            }
            Assert.fail(builder.toString());
        }
    }

    @Test
    public void testPlatformMetadataLazy()
    {
        testMetadataLazy(platformMetadataName, expectedPlatformCountsByClassifierId, expectedPlatformStats);
    }

    @Test
    public void testCoreMetadataLazy()
    {
        testMetadataLazy(coreMetadataName, expectedCoreCountsByClassifierId, expectedCoreStats);
    }

    private void testMetadataLazy(String metadataName, ObjectIntMap<String> expectedCountsByClassifierId, MetadataStats expectedStats)
    {
        MetadataLazy metadata = MetadataLazy.fromClassLoader(classLoaderWithMetadata, metadataName);
        ObjectIntMap<String> actualCountsByClassifierId = countInstancesByClassifierId(metadata);
        if (!expectedCountsByClassifierId.equals(actualCountsByClassifierId))
        {
            StringBuilder builder = new StringBuilder("Instance count by classifier mismatch:");
            GraphStatistics.writeInstanceCountsByClassifierPathDeltas(builder, "%n\t%s %,d != %,d (delta: %,d)", expectedCountsByClassifierId, actualCountsByClassifierId);
            Assert.fail(builder.toString());
        }
        MetadataStats actualStats = computeMetadataStats(metadata);
        assertMetadataStatsEqual(expectedStats, actualStats);
    }

    private static void assertMetadataStatsEqual(MetadataStats expected, MetadataStats actual)
    {
        MutableList<String> missingElements = expected.getElementPaths().reject(actual::hasElement, Lists.mutable.empty()).sortThis();
        if (missingElements.notEmpty())
        {
            Assert.fail(missingElements.makeString("Missing elements (" + missingElements.size() + "):\n", "\n", ""));
        }

        MutableList<String> extraElements = actual.getElementPaths().reject(expected::hasElement, Lists.mutable.empty()).sortThis();
        if (extraElements.notEmpty())
        {
            Assert.fail(extraElements.makeString("Extra elements (" + extraElements.size() + "):\n", "\n", ""));
        }

        MutableList<String> mismatchMessages = Lists.mutable.empty();
        expected.getElementPaths().toSortedList().forEach(path ->
        {
            ObjectIntMap<String> expectedCounts = expected.getElementPropertyValueCounts(path);
            ObjectIntMap<String> actualCounts = actual.getElementPropertyValueCounts(path);

            StringBuilder builder = new StringBuilder();
            Sets.mutable.withAll(expectedCounts.keySet()).withAll(actualCounts.keySet()).toSortedList().forEach(property ->
            {
                int expectedCount = expectedCounts.getIfAbsent(property, 0);
                int actualCount = actualCounts.getIfAbsent(property, 0);
                if (expectedCount != actualCount)
                {
                    if (builder.length() == 0)
                    {
                        builder.append("\t").append(path).append(":");
                    }
                    builder.append("\n\t\t").append(property).append(" ").append(expectedCount).append(" != ").append(actualCount);
                }
            });
            if (builder.length() > 0)
            {
                mismatchMessages.add(builder.toString());
            }
        });
        if (mismatchMessages.notEmpty())
        {
            Assert.fail(mismatchMessages.makeString("Property value mismatches for " + mismatchMessages.size() + " elements:\n", "\n", ""));
        }
    }

    private static ObjectIntMap<String> countInstancesByClassifierId(PureRuntime runtime)
    {
        ImmutableSet<String> excludedClassifierIds = AnyStubHelper.getStubClasses().newWithAll(PrimitiveUtilities.getPrimitiveTypeNames());
        MutableObjectIntMap<String> counts = ObjectIntMaps.mutable.empty();
        GraphNodeIterable.fromModelRepository(runtime.getModelRepository())
                .collect(CoreInstance::getClassifier)
                .collect(org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement::getUserPathForPackageableElement)
                .reject(excludedClassifierIds::contains)
                .forEach(id -> counts.addToValue(id, 1));
        return counts.asUnmodifiable();
    }

    private static ObjectIntMap<String> countInstancesByClassifierId(MetadataLazy metadataLazy)
    {
        ImmutableSet<String> excludedClassifierIds = AnyStubHelper.getStubClasses().newWithAll(PrimitiveUtilities.getPrimitiveTypeNames());
        MutableObjectIntMap<String> counts = ObjectIntMaps.mutable.empty();
        CompiledProcessorSupport processorSupport = new CompiledProcessorSupport(classLoaderWithMetadata, metadataLazy, Sets.immutable.empty());
        MutableSet<CoreInstance> visited = Sets.mutable.empty();
        Deque<CoreInstance> deque = new ArrayDeque<>();
        deque.add(processorSupport.repository_getTopLevel(M3Paths.Root));
        while (!deque.isEmpty())
        {
            CoreInstance node = deque.removeLast();
            CoreInstance classifier = processorSupport.getClassifier(node);
            String classifierPath = org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(classifier);
            if (!excludedClassifierIds.contains(classifierPath) && visited.add(node))
            {
                counts.addToValue(classifierPath, 1);
                processorSupport.class_getSimplePropertiesByName(classifier).forEachKey(key -> Iterate.addAllIterable(node.getValueForMetaPropertyToMany(key), deque));
            }
        }
        return counts.asUnmodifiable();
    }

    private static MetadataStats computeMetadataStats(PureRuntime runtime)
    {
        return computeMetadataStats(runtime.getProcessorSupport());
    }

    private static MetadataStats computeMetadataStats(MetadataLazy metadataLazy)
    {
        return computeMetadataStats(new CompiledProcessorSupport(classLoaderWithMetadata, metadataLazy, Sets.immutable.empty()));
    }

    private static MetadataStats computeMetadataStats(ProcessorSupport processorSupport)
    {
        MetadataStats stats = new MetadataStats();
        PackageTreeIterable.newRootPackageTreeIterable(processorSupport).forEach(pkg ->
        {
            if (!isSystemImports(pkg))
            {
                stats.collectStats(pkg, processorSupport);
                pkg._children().asLazy().reject(Package.class::isInstance).forEach(c -> stats.collectStats(c, processorSupport));
            }
        });
        return stats;
    }

    private static boolean isSystemImports(Package pkg)
    {
        if ("imports".equals(pkg._name()))
        {
            Package parent = pkg._package();
            if ((parent != null) && "system".equals(parent._name()))
            {
                Package grandparent = parent._package();
                return (grandparent != null) &&
                        M3Paths.Root.equals(grandparent._name()) &&
                        (grandparent._package() == null);
            }
        }
        return false;
    }

    private static class MetadataStats
    {
        private final MutableMap<String, ObjectIntMap<String>> propertyValueCounts = Maps.mutable.empty();

        private void collectStats(PackageableElement element, ProcessorSupport processorSupport)
        {
            String path = org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(element);
            if (this.propertyValueCounts.containsKey(path))
            {
                throw new RuntimeException("Already collected stats for " + path);
            }

            RichIterable<String> properties = processorSupport.class_getSimplePropertiesByName(element.getClassifier()).keysView();
            MutableObjectIntMap<String> countsByProperty = ObjectIntMaps.mutable.empty();
            properties.forEach(key -> countsByProperty.put(key, element.getValueForMetaPropertyToMany(key).size()));
            this.propertyValueCounts.put(path, countsByProperty.asUnmodifiable());
        }

        private boolean hasElement(String path)
        {
            return this.propertyValueCounts.containsKey(path);
        }

        private RichIterable<String> getElementPaths()
        {
            return this.propertyValueCounts.keysView();
        }

        private ObjectIntMap<String> getElementPropertyValueCounts(String path)
        {
            return this.propertyValueCounts.get(path);
        }
    }
}
