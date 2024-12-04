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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.pure.m3.serialization.compiler.reference.AbstractReferenceTest;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestModuleMetadataGenerator extends AbstractReferenceTest
{
    private ModuleMetadataGenerator generator;
    private final MutableList<String> testSources = Lists.mutable.empty();

    @Before
    public void setUpGenerator()
    {
        generator = newGenerator();
    }

    @Before
    public void clearTestSources()
    {
        this.testSources.clear();
    }

    @After
    public void deleteTestSources()
    {
        if (this.testSources.notEmpty())
        {
            this.testSources.forEach(runtime::delete);
            runtime.compile();
        }
    }

    @Test
    public void testEmptyModule()
    {
        String name = "empty";
        Assert.assertEquals(new ModuleMetadata(name), generator.generateModuleMetadata(name));
    }

    @Test
    public void testRefTestModule()
    {
        String name = "ref_test";
        Assert.assertEquals(getModuleMetadata(name), generator.generateModuleMetadata(name));
    }

    @Test
    public void testPlatformModule()
    {
        String name = "platform";
        Assert.assertEquals(getModuleMetadata(name), generator.generateModuleMetadata(name));
    }

    @Test
    public void testMultiModules()
    {
        String[] names = {"platform", "ref_test"};
        Assert.assertEquals(
                getModuleMetadata(names).sortThisBy(ModuleMetadata::getName),
                generator.generateModuleMetadata(names).sortThisBy(ModuleMetadata::getName));
    }

    @Test
    public void testAllModules()
    {
        Assert.assertEquals(
                getAllModuleMetadata().sortThisBy(ModuleMetadata::getName),
                generator.generateAllModuleMetadata().sortThisBy(ModuleMetadata::getName)
        );
    }

    @Test
    public void testUpdateElements()
    {
        String name = "ref_test";
        ModuleMetadata baseMetadata = generator.generateModuleMetadata(name);

        String sourceId = "/" + name + "/more_test_code.pure";
        compileTestCode(
                sourceId,
                "Class test::model::NewClass\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Profile test::profiles::NewProfile\n" +
                        "{\n" +
                        "  stereotypes : [a, b, c];\n" +
                        "  tags : [w, x, y, z];\n" +
                        "}\n");
        ModuleMetadataGenerator newGenerator1 = newGenerator();
        ModuleMetadata newMetadata1 = newGenerator1.generateModuleMetadata(name);
        Assert.assertEquals(
                newMetadata1,
                newGenerator1.updateElements(baseMetadata, "system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile"));
        Assert.assertEquals(
                newMetadata1.withoutElements("system::imports::import__ref_test_more_test_code_pure_1"),
                newGenerator1.updateElements(baseMetadata, "test::model::NewClass", "test::profiles::NewProfile"));

        modifyTestCode(
                sourceId,
                "Class test::model2::OtherNewClass\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Profile test::profiles2::OtherNewProfile\n" +
                        "{\n" +
                        "  stereotypes : [a, b, c];\n" +
                        "  tags : [w, x, y, z];\n" +
                        "}\n");
        ModuleMetadataGenerator newGenerator2 = newGenerator();
        ModuleMetadata newMetadata2 = newGenerator2.generateModuleMetadata(name);
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.updateElements(baseMetadata, "system::imports::import__ref_test_more_test_code_pure_1", "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"));
        Assert.assertEquals(
                newGenerator2.updateElements(baseMetadata, "system::imports::import__ref_test_more_test_code_pure_1"),
                newGenerator2.updateElements(newMetadata1, "system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile"));
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.updateElements(newMetadata1, "system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile", "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"));
    }

    @Test
    public void testUpdateAllElements()
    {
        String name = "ref_test";
        ModuleMetadata baseMetadata = generator.generateModuleMetadata(name);

        String sourceId = "/" + name + "/more_test_code.pure";
        compileTestCode(
                sourceId,
                "Class test::model::NewClass\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Profile test::profiles::NewProfile\n" +
                        "{\n" +
                        "  stereotypes : [a, b, c];\n" +
                        "  tags : [w, x, y, z];\n" +
                        "}\n");
        ModuleMetadataGenerator newGenerator1 = newGenerator();
        ModuleMetadata newMetadata1 = newGenerator1.generateModuleMetadata(name);
        Assert.assertSame(baseMetadata, newGenerator1.updateAllElements(baseMetadata));
        Assert.assertEquals(
                newMetadata1,
                newGenerator1.updateAllElements(baseMetadata, "system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile"));
        Assert.assertEquals(
                newMetadata1.withoutElements("system::imports::import__ref_test_more_test_code_pure_1"),
                newGenerator1.updateAllElements(baseMetadata, "test::model::NewClass", "test::profiles::NewProfile"));

        modifyTestCode(
                sourceId,
                "Class test::model2::OtherNewClass\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Profile test::profiles2::OtherNewProfile\n" +
                        "{\n" +
                        "  stereotypes : [a, b, c];\n" +
                        "  tags : [w, x, y, z];\n" +
                        "}\n");
        ModuleMetadataGenerator newGenerator2 = newGenerator();
        ModuleMetadata newMetadata2 = newGenerator2.generateModuleMetadata(name);
        Assert.assertEquals(
                newMetadata2.withoutElements("test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"),
                newGenerator2.updateAllElements(newMetadata1));
        Assert.assertEquals(
                newMetadata1.withoutElements("test::model::NewClass", "test::profiles::NewProfile"),
                newGenerator2.updateAllElements(newMetadata1));
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.updateAllElements(baseMetadata, "system::imports::import__ref_test_more_test_code_pure_1", "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"));
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.updateAllElements(newMetadata1, "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"));
    }

    private ModuleMetadata getModuleMetadata(String moduleName)
    {
        return new ModuleMetadata(
                moduleName,
                GraphTools.getTopLevelAndPackagedElements(repository).collectIf(
                        e -> (e.getSourceInformation() != null) && moduleName.equals(CodeStorageTools.getInitialPathElement(e.getSourceInformation().getSourceId())),
                        generator.getElementMetadataGenerator()::generateMetadata,
                        Lists.mutable.empty()));
    }

    private MutableList<ModuleMetadata> getModuleMetadata(String... moduleNames)
    {
        MutableMap<String, MutableList<ConcreteElementMetadata>> byModule = Maps.mutable.ofInitialCapacity(moduleNames.length);
        ArrayIterate.forEach(moduleNames, name -> byModule.put(name, Lists.mutable.empty()));
        GraphTools.getTopLevelAndPackagedElements(repository).forEach(element ->
        {
            SourceInformation sourceInfo = element.getSourceInformation();
            if (sourceInfo != null)
            {
                MutableList<ConcreteElementMetadata> metadatas = byModule.get(CodeStorageTools.getInitialPathElement(sourceInfo.getSourceId()));
                if (metadatas != null)
                {
                    metadatas.add(generator.getElementMetadataGenerator().generateMetadata(element));
                }
            }
        });
        MutableList<ModuleMetadata> result = Lists.mutable.ofInitialCapacity(byModule.size());
        byModule.forEachKeyValue((name, metadatas) -> result.add(new ModuleMetadata(name, metadatas)));
        return result;
    }

    private MutableList<ModuleMetadata> getAllModuleMetadata()
    {
        MutableMap<String, MutableList<ConcreteElementMetadata>> byModule = Maps.mutable.empty();
        GraphTools.getTopLevelAndPackagedElements(repository).forEach(element ->
        {
            SourceInformation sourceInfo = element.getSourceInformation();
            if (sourceInfo != null)
            {
                ConcreteElementMetadata metadata = generator.getElementMetadataGenerator().generateMetadata(element);
                byModule.getIfAbsentPut(CodeStorageTools.getInitialPathElement(sourceInfo.getSourceId()), Lists.mutable::empty).add(metadata);
            }
        });
        MutableList<ModuleMetadata> result = Lists.mutable.ofInitialCapacity(byModule.size());
        byModule.forEachKeyValue((name, metadatas) -> result.add(new ModuleMetadata(name, metadatas)));
        return result;
    }

    private void compileTestCode(String sourceId, String code)
    {
        this.testSources.add(sourceId);
        compileTestSource(sourceId, code);
    }

    private void modifyTestCode(String sourceId, String code)
    {
        if (!this.testSources.contains(sourceId))
        {
            throw new RuntimeException("Not a test source: " + sourceId);
        }
        runtime.modify(sourceId, code);
        runtime.compile();
    }

    private static ModuleMetadataGenerator newGenerator()
    {
        return new ModuleMetadataGenerator(processorSupport);
    }
}
