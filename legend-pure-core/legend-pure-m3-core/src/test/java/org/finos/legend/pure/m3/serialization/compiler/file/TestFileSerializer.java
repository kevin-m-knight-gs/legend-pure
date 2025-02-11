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

package org.finos.legend.pure.m3.serialization.compiler.file;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.serialization.compiler.element.ConcreteElementSerializer;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedConcreteElement;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataGenerator;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializer;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.classpath.ClassLoaderCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.composite.CompositeCodeStorage;
import org.finos.legend.pure.m3.tests.AbstractPureTestWithCoreCompiled;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.serialization.binary.BinaryReaders;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

public class TestFileSerializer extends AbstractPureTestWithCoreCompiled
{
    @ClassRule
    public static TemporaryFolder TMP = new TemporaryFolder();
    private static ConcreteElementSerializer elementSerializer;
    private static FileSerializer fileSerializer;

    @BeforeClass
    public static void setUpRuntime()
    {
        setUpRuntime(getFunctionExecution(), new CompositeCodeStorage(new ClassLoaderCodeStorage(getCodeRepositories())), getExtra());
        elementSerializer = ConcreteElementSerializer.builder(processorSupport).withLoadedExtensions().build();
        fileSerializer = FileSerializer.builder()
                .withFilePathProvider(FilePathProvider.builder().withLoadedExtensions().build())
                .withSerializers(elementSerializer, ModuleMetadataSerializer.builder().withLoadedExtensions().build())
                .build();
    }

    @Test
    public void testAllElementsInDirectory() throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        MutableMap<String, DeserializedConcreteElement> expectedElements = Maps.mutable.empty();
        GraphTools.getTopLevelAndPackagedElements(processorSupport).select(e -> e.getSourceInformation() != null).forEach(element ->
        {
            String elementPath = PackageableElement.getUserPathForPackageableElement(element);
            expectedElements.put(elementPath, getExpectedDeserializedElement(element));
            fileSerializer.serializeElement(directory, element);
        });

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null))
        {
            expectedElements.forEachKeyValue((elementPath, expected) ->
                    Assert.assertEquals(elementPath, expected, fileSerializer.deserializeElement(classLoader, elementPath)));
            expectedElements.forEachKey(elementPath -> Assert.assertTrue(elementPath, fileSerializer.elementExists(classLoader, elementPath)));
        }

        expectedElements.forEachKeyValue((elementPath, expected) ->
                Assert.assertEquals(elementPath, expected, fileSerializer.deserializeElement(directory, elementPath)));
        expectedElements.forEachKey(elementPath -> Assert.assertTrue(elementPath, fileSerializer.elementExists(directory, elementPath)));
    }

    @Test
    public void testAllElementsInJar() throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        Path jarPath = directory.resolve("elements_test.jar");
        MutableMap<String, DeserializedConcreteElement> expectedElements = Maps.mutable.empty();
        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath))))
        {
            GraphTools.getTopLevelAndPackagedElements(processorSupport).select(e -> e.getSourceInformation() != null).forEach(element ->
            {
                String elementPath = PackageableElement.getUserPathForPackageableElement(element);
                expectedElements.put(elementPath, getExpectedDeserializedElement(element));
                fileSerializer.serializeElement(jarStream, element);
            });
        }
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, null))
        {
            expectedElements.forEachKeyValue((elementPath, expected) ->
                    Assert.assertEquals(elementPath, expected, fileSerializer.deserializeElement(classLoader, elementPath)));
        }
    }

    private DeserializedConcreteElement getExpectedDeserializedElement(CoreInstance element)
    {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        elementSerializer.serialize(BinaryWriters.newBinaryWriter(byteStream), element);
        return elementSerializer.deserialize(BinaryReaders.newBinaryReader(byteStream.toByteArray()));
    }

    @Test
    public void testAllModulesInDirectory() throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        MutableList<ModuleMetadata> allModuleMetadata = new ModuleMetadataGenerator(processorSupport).generateAllModuleMetadata();
        allModuleMetadata.forEach(m -> fileSerializer.serializeModuleMetadata(directory, m));

        allModuleMetadata.forEach(m -> Assert.assertTrue(m.getName(), fileSerializer.moduleMetadataExists(directory, m.getName())));
        allModuleMetadata.forEach(m -> Assert.assertEquals(m.getName(), m, fileSerializer.deserializeModuleMetadata(directory, m.getName())));
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null))
        {
            allModuleMetadata.forEach(m -> Assert.assertTrue(m.getName(), fileSerializer.moduleMetadataExists(classLoader, m.getName())));
            allModuleMetadata.forEach(m -> Assert.assertEquals(m.getName(), m, fileSerializer.deserializeModuleMetadata(classLoader, m.getName())));
        }
    }

    @Test
    public void testAllModulesInJar() throws IOException
    {
        Path directory = TMP.newFolder().toPath();
        Path jarPath = directory.resolve("modules_test.jar");
        MutableList<ModuleMetadata> allModuleMetadata = new ModuleMetadataGenerator(processorSupport).generateAllModuleMetadata();
        try (JarOutputStream jarStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath))))
        {
            allModuleMetadata.forEach(m -> fileSerializer.serializeModuleMetadata(jarStream, m));
        }

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, null))
        {
            allModuleMetadata.forEach(m -> Assert.assertTrue(m.getName(), fileSerializer.moduleMetadataExists(classLoader, m.getName())));
            allModuleMetadata.forEach(m -> Assert.assertEquals(m.getName(), m, fileSerializer.deserializeModuleMetadata(classLoader, m.getName())));
        }
    }
}
