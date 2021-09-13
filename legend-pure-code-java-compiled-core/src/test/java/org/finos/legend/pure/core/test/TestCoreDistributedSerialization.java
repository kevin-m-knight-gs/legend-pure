package org.finos.legend.pure.core.test;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.finos.legend.pure.code.core.CoreCodeRepositoryProvider;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.serialization.filesystem.PureCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.repository.PlatformCodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.classpath.ClassLoaderCodeStorage;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntimeBuilder;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
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
import java.util.Collections;
import java.util.Comparator;
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

    private static SetIterable<String> platformClassifierIdsForSerialization;
    private static SetIterable<String> coreClassifierIdsForSerialization;

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
        platformClassifierIdsForSerialization = computeClassifierIdsForSerialization(runtime);
        expectedPlatformStats = computeMetadataStats(runtime);

        DistributedBinaryGraphSerializer.newSerializer(DistributedBinaryMetadata.newMetadata(platformMetadataName), runtime).serializeToDirectory(platformMetadataDir);
        DistributedBinaryMetadata.newMetadata(platformMetadataName).writeMetadataDefinition(platformMetadataDir);
    }

    private static void buildCoreMetadata(ClassLoader classLoader)
    {
        PureCodeStorage codeStorage = new PureCodeStorage(null, new ClassLoaderCodeStorage(classLoader, PlatformCodeRepository.newPlatformCodeRepository(), new CoreCodeRepositoryProvider().repository()));
        PureRuntime runtime = new PureRuntimeBuilder(codeStorage).setTransactionalByDefault(false).buildAndInitialize();
        coreClassifierIdsForSerialization = computeClassifierIdsForSerialization(runtime);
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
        Assert.assertEquals(Collections.emptySet(), platformClassifierIdsForSerialization.reject(classifiers::contains));
        Assert.assertEquals(Collections.emptySet(), classifiers.reject(platformClassifierIdsForSerialization::contains));

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
        Assert.assertEquals(Collections.emptySet(), coreClassifierIdsForSerialization.reject(classifiers::contains));
        Assert.assertEquals(Collections.emptySet(), classifiers.reject(coreClassifierIdsForSerialization::contains));
        Assert.assertEquals(Collections.emptySet(), platformClassifierIdsForSerialization.reject(classifiers::contains));
        Assert.assertNotEquals(Collections.emptySet(), classifiers.reject(platformClassifierIdsForSerialization::contains));

        MutableList<Obj> shouldBeObjUpdate = Lists.mutable.empty();
        MutableList<ObjUpdate> shouldBeObj = Lists.mutable.empty();
        coreClassifierIdsForSerialization.forEach(classifierId ->
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
        MetadataLazy platformMetadata = MetadataLazy.fromClassLoader(classLoaderWithMetadata, platformMetadataName);
        MetadataStats actualPlatformStats = computeMetadataStats(platformMetadata);
        assertMetadataStatsEqual(expectedPlatformStats, actualPlatformStats);
    }

    @Test
    public void testCoreMetadataLazy()
    {
        MetadataLazy coreMetadata = MetadataLazy.fromClassLoader(classLoaderWithMetadata, coreMetadataName);
        MetadataStats actualCoreStats = computeMetadataStats(coreMetadata);
        assertMetadataStatsEqual(expectedCoreStats, actualCoreStats);
    }

    private static void assertMetadataStatsEqual(MetadataStats expected, MetadataStats actual)
    {
        MutableList<String> missingElements = expected.referenceUsageCounts.keysView().reject(actual.referenceUsageCounts::containsKey, Lists.mutable.empty()).sortThis();
        Assert.assertEquals(Collections.emptyList(), missingElements);

        MutableList<String> extraElements = actual.referenceUsageCounts.keysView().reject(expected.referenceUsageCounts::containsKey, Lists.mutable.empty()).sortThis();
        Assert.assertEquals(Collections.emptyList(), extraElements);

        MutableList<String> referenceUsageMismatches = Lists.mutable.empty();
        expected.referenceUsageCounts.forEachKeyValue((path, expectedCount) ->
        {
            int actualCount = actual.referenceUsageCounts.getIfAbsent(path, -1);
            if (expectedCount != actualCount)
            {
                referenceUsageMismatches.add(path + ": " + expectedCount + " != " + actualCount);
            }
        });
        if (!referenceUsageMismatches.isEmpty())
        {
            Assert.fail(referenceUsageMismatches.sortThis().makeString("Reference usage mismatch for " + referenceUsageMismatches.size() + " elements:\n", "\n", ""));
        }

        MutableList<String> functionApplicationMismatches = Lists.mutable.empty();
        expected.functionApplicationCounts.forEachKeyValue((path, expectedCount) ->
        {
            int actualCount = actual.functionApplicationCounts.getIfAbsent(path, -1);
            if (expectedCount != actualCount)
            {
                functionApplicationMismatches.add(path + ": " + expectedCount + " != " + actualCount);
            }
        });
        if (!functionApplicationMismatches.isEmpty())
        {
            Assert.fail(functionApplicationMismatches.sortThis().makeString("Function application mismatch for " + referenceUsageMismatches.size() + " elements:\n", "\n", ""));
        }
    }

    private static SetIterable<String> computeClassifierIdsForSerialization(PureRuntime runtime)
    {
        ImmutableSet<String> excludedClassifierIds = Sets.immutable.with(M3Paths.EnumStub, M3Paths.GrammarInfoStub, M3Paths.ImportStub, M3Paths.PropertyStub)
                .newWithAll(PrimitiveUtilities.getPrimitiveTypeNames());
        return GraphNodeIterable.fromModelRepository(runtime.getModelRepository())
                .collect(CoreInstance::getClassifier)
                .collect(org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement::getUserPathForPackageableElement)
                .reject(excludedClassifierIds::contains, Sets.mutable.empty())
                .asUnmodifiable();
    }

    private static MetadataStats computeMetadataStats(PureRuntime runtime)
    {
        return computeMetadataStats(PackageTreeIterable.newRootPackageTreeIterable(runtime.getModelRepository()));
    }

    private static MetadataStats computeMetadataStats(MetadataLazy metadataLazy)
    {
        return computeMetadataStats(PackageTreeIterable.newPackageTreeIterable((Package) metadataLazy.getMetadata(M3Paths.Package, M3Paths.Root)));
    }

    private static MetadataStats computeMetadataStats(PackageTreeIterable packageTreeIterable)
    {
        MetadataStats stats = new MetadataStats();
        packageTreeIterable.forEach(pkg ->
        {
            if (!isSystemImports(pkg))
            {
                stats.collectStats(pkg);
                pkg._children().asLazy().reject(Package.class::isInstance).forEach(stats::collectStats);
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
        private final MutableObjectIntMap<String> referenceUsageCounts = ObjectIntMaps.mutable.empty();
        private final MutableObjectIntMap<String> functionApplicationCounts = ObjectIntMaps.mutable.empty();

        private void collectStats(PackageableElement element)
        {
            String path = org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(element);
            if (this.referenceUsageCounts.containsKey(path))
            {
                throw new RuntimeException("Already collected stats for " + path);
            }
            this.referenceUsageCounts.put(path, element._referenceUsages().size());
            if (element instanceof Function)
            {
                this.functionApplicationCounts.put(path, ((Function<?>) element)._applications().size());
            }
        }
    }
}
