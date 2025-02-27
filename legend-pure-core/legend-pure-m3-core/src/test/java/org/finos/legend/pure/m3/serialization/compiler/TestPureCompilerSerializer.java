// Copyright 2025 Goldman Sachs
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

package org.finos.legend.pure.m3.serialization.compiler;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.serialization.compiler.element.ConcreteElementSerializer;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedConcreteElement;
import org.finos.legend.pure.m3.serialization.compiler.file.FilePathProvider;
import org.finos.legend.pure.m3.serialization.compiler.file.FileSerializer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataGenerator;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializer;
import org.finos.legend.pure.m3.serialization.compiler.reference.AbstractReferenceTest;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.classpath.ClassLoaderCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.composite.CompositeCodeStorage;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.jar.JarOutputStream;

public class TestPureCompilerSerializer extends AbstractReferenceTest
{
    @ClassRule
    public static TemporaryFolder TMP = new TemporaryFolder();

    private static FileSerializer fileSerializer;
    private static ModuleMetadataGenerator moduleMetadataGenerator;
    private static PureCompilerSerializer pureCompilerSerializer;

    @BeforeClass
    public static void setUpRuntime()
    {
        setUpRuntime(getFunctionExecution(), new CompositeCodeStorage(new ClassLoaderCodeStorage(getCodeRepositories())), getExtra());
        fileSerializer = FileSerializer.builder()
                .withFilePathProvider(FilePathProvider.builder().withLoadedExtensions().build())
                .withSerializers(ConcreteElementSerializer.builder(processorSupport).withLoadedExtensions().build(), ModuleMetadataSerializer.builder().withLoadedExtensions().build())
                .build();
        moduleMetadataGenerator = ModuleMetadataGenerator.fromPureRuntime(runtime);
        pureCompilerSerializer = PureCompilerSerializer.builder()
                .withFileSerializer(fileSerializer)
                .withModuleMetadataGenerator(moduleMetadataGenerator)
                .withProcessorSupport(processorSupport)
                .build();
    }

    @Test
    public void testSerializeAllToDirectory() throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        pureCompilerSerializer.serializeAll(directory);
        getAllModuleInfos().forEachKeyValue((moduleName, moduleInfo) ->
        {
            Assert.assertTrue(moduleName, fileSerializer.moduleMetadataExists(directory, moduleName));
            Assert.assertEquals(moduleName, moduleInfo.metadata, fileSerializer.deserializeModuleMetadata(directory, moduleName));
            moduleInfo.elements.forEachKeyValue((path, element) ->
            {
                Assert.assertTrue(path, fileSerializer.elementExists(directory, path));
                DeserializedConcreteElement deserialized = fileSerializer.deserializeElement(directory, path);
                Assert.assertEquals(path, element.getSourceInformation(), deserialized.getConcreteElementData().getSourceInformation());
            });
        });
    }

    @Test
    public void testSerializeAllToJar() throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        Path jarPath = directory.resolve("test.jar");
        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath))))
        {
            pureCompilerSerializer.serializeAll(jarStream);
        }
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, null))
        {
            getAllModuleInfos().forEachKeyValue((moduleName, moduleInfo) ->
            {
                Assert.assertTrue(moduleName, fileSerializer.moduleMetadataExists(classLoader, moduleName));
                Assert.assertEquals(moduleName, moduleInfo.metadata, fileSerializer.deserializeModuleMetadata(classLoader, moduleName));
                moduleInfo.elements.forEachKeyValue((path, element) ->
                {
                    Assert.assertTrue(path, fileSerializer.elementExists(classLoader, path));
                    DeserializedConcreteElement deserialized = fileSerializer.deserializeElement(classLoader, path);
                    Assert.assertEquals(path, element.getSourceInformation(), deserialized.getConcreteElementData().getSourceInformation());
                });
            });
        }
    }

    @Test
    public void testSerializePlatformToDirectory() throws IOException
    {
        testSerializeModuleToDirectory("platform");
    }

    @Test
    public void testSerializePlatformToJar() throws IOException
    {
        testSerializeModuleToJar("platform");
    }

    @Test
    public void testSerializeTestModuleToDirectory() throws IOException
    {
        testSerializeModuleToDirectory("ref_test");
    }

    @Test
    public void testSerializeTestModuleToJar() throws IOException
    {
        testSerializeModuleToJar("ref_test");
    }

    @Test
    public void testSerializeModulesToDirectory() throws IOException
    {
        testSerializeModulesToDirectory("test_generic_repository", "other_test_generic_repository", "ref_test");
    }

    @Test
    public void testSerializeModulesToJar() throws IOException
    {
        testSerializeModulesToJar("test_generic_repository", "other_test_generic_repository", "ref_test");
    }

    @Test
    public void testSerializeElement() throws IOException
    {
        Path directory = TMP.newFolder().toPath();

        MutableMap<String, ModuleInfo> moduleInfos = getModuleInfos("platform", "ref_test");

        MutableSet<String> expectedElements = Sets.mutable.empty();
        MutableList<String> toSerialize = Lists.mutable.with("test::model::SimpleClass", "test::model::LeftRight", "test::model::BothSides", M3Paths.access, M3Paths.Association, M3Paths.Class, M3Paths.equality);
        toSerialize.forEach(elementPath ->
        {
            expectedElements.add(elementPath);
            pureCompilerSerializer.serializeElement(directory, elementPath);
            assertElements(directory, moduleInfos, expectedElements);
        });
    }

    @Test
    public void testSerializeElements() throws IOException
    {
        Path directory = TMP.newFolder().toPath();

        MutableMap<String, ModuleInfo> moduleInfos = getModuleInfos("platform", "ref_test");

        MutableSet<String> expectedElements = Sets.mutable.empty();
        MutableList<MutableList<String>> toSerialize = Lists.fixedSize.with(
                Lists.mutable.with("test::model::SimpleClass"),
                Lists.mutable.with("test::model::LeftRight", "test::model::Left", "test::model::Right", "test::model::BothSides", M3Paths.Class, M3Paths.equality),
                Lists.mutable.with("test::model::ClassWithMilestoning1", "test::model::ClassWithMilestoning2", "test::model::ClassWithMilestoning3", M3Paths.Association, M3Paths.access));
        toSerialize.forEach(elementPaths ->
        {
            expectedElements.addAll(elementPaths);
            pureCompilerSerializer.serializeElements(directory, elementPaths);
            assertElements(directory, moduleInfos, expectedElements);
        });
    }

    @Test
    public void testSerializeElements_Predicate() throws IOException
    {
        Path directory = TMP.newFolder().toPath();

        MutableMap<String, ModuleInfo> moduleInfos = getModuleInfos("platform", "ref_test");

        MutableSet<String> expectedElements = Sets.mutable.empty();
        MutableList<Predicate<? super CoreInstance>> predicates = Lists.fixedSize.with(
                e -> "test::model::SimpleClass".equals(PackageableElement.getUserPathForPackageableElement(e)),
                e -> e.getName().contains("Left") || e.getName().contains("Right") || M3Paths.equality.equals(PackageableElement.getUserPathForPackageableElement(e)),
                e ->
                {
                    String path = PackageableElement.getUserPathForPackageableElement(e);
                    return M3Paths.Class.equals(path) || M3Paths.Association.equals(path) || M3Paths.access.equals(path);
                });
        predicates.forEach(predicate ->
        {
            moduleInfos.valuesView().flatCollect(mi -> mi.elements.valuesView()).collectIf(predicate::test, PackageableElement::getUserPathForPackageableElement, expectedElements);
            pureCompilerSerializer.serializeElements(directory, predicate);
            assertElements(directory, moduleInfos, expectedElements);
        });
    }

    private void testSerializeModuleToDirectory(String moduleName) throws IOException
    {
        Path directory = TMP.newFolder().toPath();

        pureCompilerSerializer.serializeModule(directory, moduleName);

        ModuleInfo moduleInfo = getModuleInfo(moduleName);
        Assert.assertTrue(moduleName, fileSerializer.moduleMetadataExists(directory, moduleName));
        Assert.assertEquals(moduleName, moduleInfo.metadata, fileSerializer.deserializeModuleMetadata(directory, moduleName));
        moduleInfo.elements.forEachKeyValue((path, element) -> assertElementSerialized(directory, path, element));
    }

    private void testSerializeModuleToJar(String moduleName) throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        Path jarPath = directory.resolve(moduleName + ".jar");

        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath))))
        {
            pureCompilerSerializer.serializeModule(jarStream, moduleName);
        }

        ModuleInfo moduleInfo = getModuleInfo(moduleName);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, null))
        {
            Assert.assertTrue(moduleName, fileSerializer.moduleMetadataExists(classLoader, moduleName));
            Assert.assertEquals(moduleName, moduleInfo.metadata, fileSerializer.deserializeModuleMetadata(classLoader, moduleName));
            moduleInfo.elements.forEachKeyValue((path, element) -> assertElementSerialized(classLoader, path, element));
        }
    }

    private void testSerializeModulesToDirectory(String... moduleNames) throws IOException
    {
        Path directory = TMP.newFolder().toPath();

        pureCompilerSerializer.serializeModules(directory, moduleNames);

        MutableMap<String, ModuleInfo> moduleInfos = getModuleInfos(moduleNames);
        Sets.mutable.with(moduleNames).forEach(moduleName ->
        {
            ModuleInfo moduleInfo = moduleInfos.get(moduleName);
            Assert.assertTrue(moduleName, fileSerializer.moduleMetadataExists(directory, moduleName));
            Assert.assertEquals(moduleName, moduleInfo.metadata, fileSerializer.deserializeModuleMetadata(directory, moduleName));
            moduleInfo.elements.forEachKeyValue((path, element) -> assertElementSerialized(directory, path, element));
        });
    }

    private void testSerializeModulesToJar(String... moduleNames) throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        Path jarPath = directory.resolve("test.jar");

        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath))))
        {
            pureCompilerSerializer.serializeModules(jarStream, moduleNames);
        }

        MutableMap<String, ModuleInfo> moduleInfos = getModuleInfos(moduleNames);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, null))
        {
            Sets.mutable.with(moduleNames).forEach(moduleName ->
            {
                ModuleInfo moduleInfo = moduleInfos.get(moduleName);
                Assert.assertTrue(moduleName, fileSerializer.moduleMetadataExists(classLoader, moduleName));
                Assert.assertEquals(moduleName, moduleInfo.metadata, fileSerializer.deserializeModuleMetadata(classLoader, moduleName));
                moduleInfo.elements.forEachKeyValue((path, element) -> assertElementSerialized(classLoader, path, element));
            });
        }
    }

    private void assertElements(Path directory, MapIterable<String, ModuleInfo> baseModuleInfos, SetIterable<String> expectedElements)
    {
        baseModuleInfos.forEachKeyValue((moduleName, moduleInfo) ->
        {
            MutableSet<String> expectedSources = Sets.mutable.empty();
            moduleInfo.elements.forEachKeyValue((path, element) ->
            {
                if (expectedElements.contains(path))
                {
                    assertElementSerialized(directory, path, element);
                    expectedSources.add(element.getSourceInformation().getSourceId());
                }
                else
                {
                    assertElementNotSerialized(directory, path);
                }
            });
            if (expectedSources.notEmpty())
            {
                Assert.assertEquals(moduleName,
                        moduleInfo.metadata
                                .withoutElements(m -> !expectedElements.contains(m.getPath()))
                                .withoutSources(s -> !expectedSources.contains(s.getSourceId())),
                        fileSerializer.deserializeModuleMetadata(directory, moduleName));
            }
            else
            {
                Assert.assertFalse(moduleName, fileSerializer.moduleMetadataExists(directory, moduleName));
            }
        });
    }

    private void assertElementSerialized(Path directory, String path, CoreInstance element)
    {
        Assert.assertTrue(path, fileSerializer.elementExists(directory, path));
        DeserializedConcreteElement deserialized = fileSerializer.deserializeElement(directory, path);
        Assert.assertEquals(path, element.getSourceInformation(), deserialized.getConcreteElementData().getSourceInformation());
    }

    private void assertElementNotSerialized(Path directory, String path)
    {
        Assert.assertFalse(path, fileSerializer.elementExists(directory, path));
    }

    private void assertElementSerialized(ClassLoader classLoader, String path, CoreInstance element)
    {
        Assert.assertTrue(path, fileSerializer.elementExists(classLoader, path));
        DeserializedConcreteElement deserialized = fileSerializer.deserializeElement(classLoader, path);
        Assert.assertEquals(path, element.getSourceInformation(), deserialized.getConcreteElementData().getSourceInformation());
    }

    private void assertElementNotSerialized(ClassLoader classLoader, String path)
    {
        Assert.assertFalse(path, fileSerializer.elementExists(classLoader, path));
    }

    private ModuleInfo getModuleInfo(String moduleName)
    {
        ModuleMetadata metadata = moduleMetadataGenerator.generateModuleMetadata(moduleName);
        MutableMap<String, CoreInstance> elements = Maps.mutable.empty();
        GraphTools.getTopLevelAndPackagedElements(processorSupport).forEach(element ->
        {
            if (ModuleHelper.isElementInModule(element, moduleName))
            {
                elements.put(PackageableElement.getUserPathForPackageableElement(element), element);
            }
        });
        return new ModuleInfo(metadata, elements);
    }

    private MutableMap<String, ModuleInfo> getModuleInfos(String... moduleNames)
    {
        MutableMap<String, ModuleInfo> moduleInfos = Maps.mutable.ofInitialCapacity(moduleNames.length);
        moduleMetadataGenerator.generateModuleMetadata(moduleNames).forEach(m -> moduleInfos.put(m.getName(), new ModuleInfo(m)));
        GraphTools.getTopLevelAndPackagedElements(processorSupport).forEach(element ->
        {
            ModuleInfo moduleInfo = moduleInfos.get(ModuleHelper.getElementModule(element));
            if (moduleInfo != null)
            {
                moduleInfo.elements.put(PackageableElement.getUserPathForPackageableElement(element), element);
            }
        });
        return moduleInfos;
    }

    private MutableMap<String, ModuleInfo> getAllModuleInfos()
    {
        MutableMap<String, ModuleInfo> moduleInfos = Maps.mutable.empty();
        moduleMetadataGenerator.generateAllModuleMetadata().forEach(m -> moduleInfos.put(m.getName(), new ModuleInfo(m)));
        GraphTools.getTopLevelAndPackagedElements(processorSupport).forEach(element ->
        {
            ModuleInfo moduleInfo = moduleInfos.get(ModuleHelper.getElementModule(element));
            if (moduleInfo != null)
            {
                moduleInfo.elements.put(PackageableElement.getUserPathForPackageableElement(element), element);
            }
        });
        return moduleInfos;
    }

    private static class ModuleInfo
    {
        private final ModuleMetadata metadata;
        private final MutableMap<String, CoreInstance> elements;

        private ModuleInfo(ModuleMetadata metadata, MutableMap<String, CoreInstance> elements)
        {
            this.metadata = metadata;
            this.elements = elements;
        }

        private ModuleInfo(ModuleMetadata metadata)
        {
            this(metadata, Maps.mutable.empty());
        }
    }
}
