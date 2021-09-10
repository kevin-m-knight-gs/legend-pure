package org.finos.legend.pure.core.test;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.finos.legend.pure.code.core.CoreCodeRepositoryProvider;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
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

    private static SetIterable<String> platformClassifierIds;
    private static SetIterable<String> coreClassifierIds;

    private static SetIterable<String> platformPackageableElementPaths;
    private static SetIterable<String> corePackageableElementPaths;

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

        ImmutableSet<String> excludedClassifierIds = Sets.immutable.with(M3Paths.EnumStub, M3Paths.GrammarInfoStub, M3Paths.ImportStub, M3Paths.PropertyStub)
                .newWithAll(PrimitiveUtilities.getPrimitiveTypeNames());
        platformClassifierIds = GraphNodeIterable.fromModelRepository(runtime.getModelRepository())
                .collect(CoreInstance::getClassifier)
                .collect(PackageableElement::getUserPathForPackageableElement)
                .reject(excludedClassifierIds::contains, Sets.mutable.empty())
                .asUnmodifiable();
        platformPackageableElementPaths = PackageTreeIterable.newRootPackageTreeIterable(runtime.getModelRepository())
                .flatCollect(Package::_children)
                .collect(PackageableElement::getUserPathForPackageableElement, Sets.mutable.empty())
                .asUnmodifiable();

        DistributedBinaryGraphSerializer.newSerializer(DistributedBinaryMetadata.newMetadata(platformMetadataName), runtime).serializeToDirectory(platformMetadataDir);
        DistributedBinaryMetadata.newMetadata(platformMetadataName).writeMetadataDefinition(platformMetadataDir);
    }

    private static void buildCoreMetadata(ClassLoader classLoader)
    {
        PureCodeStorage codeStorage = new PureCodeStorage(null, new ClassLoaderCodeStorage(classLoader, PlatformCodeRepository.newPlatformCodeRepository(), new CoreCodeRepositoryProvider().repository()));
        PureRuntime runtime = new PureRuntimeBuilder(codeStorage).setTransactionalByDefault(false).buildAndInitialize();

        ImmutableSet<String> excludedClassifierIds = Sets.immutable.with(M3Paths.EnumStub, M3Paths.GrammarInfoStub, M3Paths.ImportStub, M3Paths.PropertyStub)
                .newWithAll(PrimitiveUtilities.getPrimitiveTypeNames());
        coreClassifierIds = GraphNodeIterable.fromModelRepository(runtime.getModelRepository())
                .collect(CoreInstance::getClassifier)
                .collect(PackageableElement::getUserPathForPackageableElement)
                .reject(excludedClassifierIds::contains, Sets.mutable.empty())
                .asUnmodifiable();
        corePackageableElementPaths = PackageTreeIterable.newRootPackageTreeIterable(runtime.getModelRepository())
                .flatCollect(Package::_children)
                .collect(PackageableElement::getUserPathForPackageableElement, Sets.mutable.empty())
                .asUnmodifiable();

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
    public void testMetadataLazy()
    {
        MetadataLazy platformMetadata = MetadataLazy.fromClassLoader(classLoaderWithMetadata, platformMetadataName);
        MetadataLazy coreMetadata = MetadataLazy.fromClassLoader(classLoaderWithMetadata, coreMetadataName);

        Assert.assertEquals(Collections.emptySet(), platformClassifierIds.select(c -> platformMetadata.getMetadata(c).isEmpty()));
        Assert.assertEquals(Collections.emptySet(), platformClassifierIds.select(c -> coreMetadata.getMetadata(c).isEmpty()));
        Assert.assertEquals(Collections.emptySet(), coreClassifierIds.select(c -> coreMetadata.getMetadata(c).isEmpty()));

        MutableSet<String> actualPlatformPackageableElementPaths = PackageTreeIterable.newPackageTreeIterable((Package) platformMetadata.getMetadata(M3Paths.Package, M3Paths.Root))
                .flatCollect(Package::_children)
                .collect(PackageableElement::getUserPathForPackageableElement, Sets.mutable.empty());
        assertStringSetsEqual(platformPackageableElementPaths, actualPlatformPackageableElementPaths);
        MutableSet<String> actualCorePackageableElementPaths = PackageTreeIterable.newPackageTreeIterable((Package) coreMetadata.getMetadata(M3Paths.Package, M3Paths.Root))
                .flatCollect(Package::_children)
                .collect(PackageableElement::getUserPathForPackageableElement, Sets.mutable.empty());
        assertStringSetsEqual(corePackageableElementPaths, actualCorePackageableElementPaths);
    }

    private void assertStringSetsEqual(SetIterable<String> expected, SetIterable<String> actual)
    {
        MutableList<String> missing = expected.reject(actual::contains, Lists.mutable.empty());
        if (missing.isEmpty() && (expected.size() == actual.size()))
        {
            // sets are equal
            return;
        }

        MutableList<String> extra = actual.reject(expected::contains, Lists.mutable.empty());
        StringBuilder builder = new StringBuilder();
        if (missing.notEmpty())
        {
            missing.sortThis().appendString(builder, "Missing:\n\t", "\n\t", "\n");
        }
        if (extra.notEmpty())
        {
            extra.sortThis().appendString(builder, "Extra:\n\t", "\n\t", "\n");
        }
        Assert.fail(builder.toString());
    }

    @Test
    public void testPlatformDeserializer()
    {
        DistributedBinaryGraphDeserializer platformDeserializer = DistributedBinaryGraphDeserializer.fromClassLoader(platformMetadataName, classLoaderWithMetadata);
        MutableSet<String> classifiers = platformDeserializer.getClassifiers().toSet();
        Assert.assertEquals(Collections.emptySet(), platformClassifierIds.reject(classifiers::contains));
        Assert.assertEquals(Collections.emptySet(), classifiers.reject(platformClassifierIds::contains));

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
        Assert.assertEquals(Collections.emptySet(), coreClassifierIds.reject(classifiers::contains));
        Assert.assertEquals(Collections.emptySet(), classifiers.reject(coreClassifierIds::contains));
        Assert.assertEquals(Collections.emptySet(), platformClassifierIds.reject(classifiers::contains));
        Assert.assertNotEquals(Collections.emptySet(), classifiers.reject(platformClassifierIds::contains));

        MutableList<Obj> shouldBeObjUpdate = Lists.mutable.empty();
        MutableList<ObjUpdate> shouldBeObj = Lists.mutable.empty();
        coreClassifierIds.forEach(classifierId ->
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
}
