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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.pure.m3.serialization.compiler.ModuleHelper;
import org.finos.legend.pure.m3.serialization.compiler.reference.AbstractReferenceTest;
import org.finos.legend.pure.m3.tools.GraphTools;
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
        this.generator = newGenerator();
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
        Assert.assertEquals(ModuleMetadata.builder(name).build(), this.generator.generateModuleMetadata(name));
    }

    @Test
    public void testRefTestModule()
    {
        String name = "ref_test";
        Assert.assertEquals(getModuleMetadata(name), this.generator.generateModuleMetadata(name));
    }

    @Test
    public void testPlatformModule()
    {
        String name = "platform";
        Assert.assertEquals(getModuleMetadata(name), this.generator.generateModuleMetadata(name));
    }

    @Test
    public void testMultiModules()
    {
        String[] names = {"platform", "ref_test"};
        Assert.assertEquals(
                getModuleMetadata(names).sortThisBy(ModuleMetadata::getName),
                this.generator.generateModuleMetadata(names).sortThisBy(ModuleMetadata::getName));
    }

    @Test
    public void testAllModules()
    {
        Assert.assertEquals(
                getAllModuleMetadata().sortThisBy(ModuleMetadata::getName),
                this.generator.generateAllModuleMetadata().sortThisBy(ModuleMetadata::getName)
        );
    }

    @Test
    public void testUpdate()
    {
        String name = "ref_test";
        ModuleMetadata baseMetadata = this.generator.generateModuleMetadata(name);

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
                newGenerator1.update(baseMetadata,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile"),
                        Lists.immutable.with(sourceId)));
        Assert.assertEquals(
                newMetadata1.withoutElements("system::imports::import__ref_test_more_test_code_pure_1"),
                newGenerator1.update(baseMetadata,
                        Lists.immutable.with("test::model::NewClass", "test::profiles::NewProfile"),
                        Lists.immutable.with(sourceId)));

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
                newGenerator2.update(baseMetadata,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1", "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"),
                        Lists.immutable.with(sourceId)));
        Assert.assertEquals(
                newGenerator2.update(baseMetadata,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1"),
                        Lists.immutable.with(sourceId)),
                newGenerator2.update(newMetadata1,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile"),
                        Lists.immutable.with(sourceId)));
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.update(newMetadata1,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile", "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"),
                        Lists.immutable.with(sourceId)));
    }

    @Test
    public void testUpdateAll()
    {
        String name = "ref_test";
        ModuleMetadata baseMetadata = this.generator.generateModuleMetadata(name);

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
        Assert.assertSame(baseMetadata, newGenerator1.updateAll(baseMetadata));
        Assert.assertEquals(
                newMetadata1,
                newGenerator1.updateAll(
                        baseMetadata,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1", "test::model::NewClass", "test::profiles::NewProfile"),
                        Lists.immutable.empty()));
        Assert.assertEquals(
                newMetadata1.withoutElements("system::imports::import__ref_test_more_test_code_pure_1"),
                newGenerator1.updateAll(
                        baseMetadata,
                        Lists.immutable.with("test::model::NewClass", "test::profiles::NewProfile"),
                        Lists.immutable.empty()));

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
                newGenerator2.updateAll(newMetadata1));
        Assert.assertEquals(
                ModuleMetadata.builder(newMetadata1)
                        .withoutElements("test::model::NewClass", "test::profiles::NewProfile")
                        .withSource(newGenerator2.getSourceMetadataGenerator().generateSourceMetadata(runtime.getSourceById("/ref_test/more_test_code.pure")), true)
                        .build(),
                newGenerator2.updateAll(newMetadata1));
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.updateAll(baseMetadata,
                        Lists.immutable.with("system::imports::import__ref_test_more_test_code_pure_1", "test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"),
                        Lists.immutable.empty()));
        Assert.assertEquals(
                newMetadata2,
                newGenerator2.updateAll(newMetadata1,
                        Lists.immutable.with("test::model2::OtherNewClass", "test::profiles2::OtherNewProfile"),
                        Lists.immutable.empty()));
    }

    private ModuleMetadata getModuleMetadata(String moduleName)
    {
        return ModuleMetadata.builder()
                .withName(moduleName)
                .withElements(GraphTools.getTopLevelAndPackagedElements(repository).collectIf(
                        e -> ModuleHelper.isElementInModule(e, moduleName),
                        this.generator.getElementMetadataGenerator()::generateMetadata))
                .withSources(runtime.getSourceRegistry().getSources().asLazy().collectIf(
                        s -> ModuleHelper.isSourceInModule(s, moduleName),
                        this.generator.getSourceMetadataGenerator()::generateSourceMetadata
                ))
                .build();
    }

    private MutableList<ModuleMetadata> getModuleMetadata(String... moduleNames)
    {
        MutableMap<String, ModuleMetadata.Builder> byModule = Maps.mutable.ofInitialCapacity(moduleNames.length);
        ArrayIterate.forEach(moduleNames, name -> byModule.put(name, ModuleMetadata.builder(name)));
        GraphTools.getTopLevelAndPackagedElements(repository).forEach(element ->
        {
            ModuleMetadata.Builder builder = byModule.get(ModuleHelper.getElementModule(element));
            if (builder != null)
            {
                builder.addElement(this.generator.getElementMetadataGenerator().generateMetadata(element));
            }
        });
        runtime.getSourceRegistry().getSources().forEach(source ->
        {
            ModuleMetadata.Builder builder = byModule.get(ModuleHelper.getSourceModule(source));
            if (builder != null)
            {
                builder.addSource(this.generator.getSourceMetadataGenerator().generateSourceMetadata(source));
            }
        });
        return byModule.collect(ModuleMetadata.Builder::build, Lists.mutable.ofInitialCapacity(byModule.size()));
    }

    private MutableList<ModuleMetadata> getAllModuleMetadata()
    {
        return getAllModuleMetadata(true);
    }

    private MutableList<ModuleMetadata> getAllModuleMetadata(boolean includeRoot)
    {
        MutableMap<String, ModuleMetadata.Builder> byModule = Maps.mutable.empty();
        GraphTools.getTopLevelAndPackagedElements(repository).forEach(element ->
        {
            String module = ModuleHelper.getElementModule(element);
            if ((module != null) && (includeRoot || ModuleHelper.isNonRootModule(module)))
            {
                ConcreteElementMetadata metadata = this.generator.getElementMetadataGenerator().generateMetadata(element);
                byModule.getIfAbsentPutWithKey(ModuleHelper.getElementModule(element), ModuleMetadata::builder).addElement(metadata);
            }
        });
        runtime.getSourceRegistry().getSources().forEach(source ->
        {
            String module = ModuleHelper.getSourceModule(source);
            if ((module != null) && (includeRoot || ModuleHelper.isNonRootModule(module)))
            {
                SourceMetadata metadata = this.generator.getSourceMetadataGenerator().generateSourceMetadata(source);
                byModule.getIfAbsentPutWithKey(module, ModuleMetadata::builder).addSource(metadata);
            }
        });
        return byModule.collect(ModuleMetadata.Builder::build, Lists.mutable.ofInitialCapacity(byModule.size()));
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
        return ModuleMetadataGenerator.fromPureRuntime(runtime);
    }
}
