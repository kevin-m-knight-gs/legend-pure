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
import org.eclipse.collections.api.list.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.function.Predicate;

public class TestModuleMetadata extends AbstractMetadataTest
{
    @Test
    public void testEmptyModule()
    {
        String name = "empty_module";
        ModuleMetadata emptyModule = ModuleMetadata.builder(name).build();
        Assert.assertEquals(name, emptyModule.getName());
        Assert.assertEquals(0, emptyModule.getElementCount());
        Assert.assertEquals(Lists.immutable.empty(), emptyModule.getElements());
        assertForEachElement(emptyModule);

        Assert.assertEquals(emptyModule, ModuleMetadata.builder(name).build());
        Assert.assertNotEquals(emptyModule, ModuleMetadata.builder(name + "_" + name).build());
        Assert.assertNotEquals(emptyModule, ModuleMetadata.builder("non_empty_module")
                .withElement(newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1, 1))
                .build());
    }

    @Test
    public void testMultiSourceModule()
    {
        String name = "test_module";

        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);
        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/other_enums.pure", 1, 1, 3, 1, 1);

        SourceMetadata classesSource = newSource("/test_module/model/classes.pure", "model::classes::MySimpleClass", "model::classes::MyOtherClass", "model::classes::MyThirdClass");
        SourceMetadata associationsSource = newSource("/test_module/model/associations.pure", "model::associations::SimpleToOther", "model::associations::SimpleToThird", "model::associations::OtherToThird");
        SourceMetadata enumsSource = newSource("/test_module/model/enums.pure", "model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration");
        SourceMetadata otherEnumsSource = newSource("/test_module/model/other_enums.pure", "model::enums::NotInTheModule");

        ModuleMetadata simpleModule = ModuleMetadata.builder(name)
                .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                .withSources(classesSource, associationsSource, enumsSource)
                .build();
        Assert.assertEquals(name, simpleModule.getName());
        Assert.assertEquals(8, simpleModule.getElementCount());
        Assert.assertEquals(Lists.immutable.with(otherToThird, simpleToOther, simpleToThird, myOtherClass, mySimpleClass, myThirdClass, myFirstEnumeration, mySecondEnumeration), simpleModule.getElements());
        assertForEachElement(simpleModule, otherToThird, simpleToOther, simpleToThird, myOtherClass, mySimpleClass, myThirdClass, myFirstEnumeration, mySecondEnumeration);
        Assert.assertEquals(3, simpleModule.getSourceCount());
        Assert.assertEquals(Lists.immutable.with(associationsSource, classesSource, enumsSource), simpleModule.getSources());
        assertForEachSource(simpleModule, associationsSource, classesSource, enumsSource);

        Assert.assertEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(otherToThird, simpleToOther, simpleToThird, myOtherClass, mySimpleClass, myThirdClass, myFirstEnumeration, mySecondEnumeration)
                        .withSources(classesSource, associationsSource, enumsSource)
                        .build());
        Assert.assertEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                        .withSources(enumsSource, associationsSource, classesSource)
                        .build());
        Assert.assertEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(myFirstEnumeration, mySimpleClass, myOtherClass, simpleToOther, simpleToThird, otherToThird, mySecondEnumeration, myThirdClass)
                        .withSources(associationsSource, enumsSource, classesSource)
                        .build());
        Assert.assertNotEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, mySecondEnumeration)
                        .withSources(classesSource, associationsSource, enumsSource)
                        .build());
        Assert.assertNotEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird)
                        .withSources(classesSource, associationsSource)
                        .build());
        Assert.assertNotEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, mySecondEnumeration)
                        .withSources(classesSource, associationsSource, enumsSource)
                        .build());
        Assert.assertNotEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration, notInTheModule)
                        .withSources(classesSource, associationsSource, enumsSource)
                        .build());
        Assert.assertNotEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                        .withSources(classesSource, associationsSource, enumsSource, otherEnumsSource)
                        .build());
        Assert.assertNotEquals(
                simpleModule,
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration, notInTheModule)
                        .withSources(classesSource, associationsSource, enumsSource, otherEnumsSource)
                        .build());
    }

    @Test
    public void testInvalidName()
    {
        NullPointerException eNull = Assert.assertThrows(NullPointerException.class, ModuleMetadata.builder()::build);
        Assert.assertEquals("name may not be null", eNull.getMessage());

        IllegalArgumentException eEmpty = Assert.assertThrows(IllegalArgumentException.class, () -> ModuleMetadata.builder().withName(""));
        Assert.assertEquals("name may not be empty", eEmpty.getMessage());
    }

    @Test
    public void testElementPathConflict()
    {
        // this should work, since the two are equal
        ModuleMetadata.builder("non_empty_module").withElements(
                newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1, 1),
                newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1, 1)
        ).build();

        IllegalArgumentException e = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> ModuleMetadata.builder("test_module").withElements(
                        newClass("model::MyClass", "/test_module/file.pure", 1, 1, 5, 1, 1),
                        newAssociation("model::MyAssociation", "/test_module/file.pure", 6, 1, 8, 1, 1),
                        newClass("model::MyClass", "/test_module/file.pure", 9, 1, 15, 1, 1)
                ).build());
        Assert.assertEquals("Conflict for element: model::MyClass", e.getMessage());
    }

    @Test
    public void testSourceIdConflict()
    {
        // this should work, since the two are equal
        ModuleMetadata.builder("non_empty_module").withSources(
                newSource("/non_empty_module/file.pure", "model::MyClass"),
                newSource("/non_empty_module/file.pure", "model::MyClass")
        ).build();

        IllegalArgumentException e = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> ModuleMetadata.builder("test_module").withSources(
                        newSource("/non_empty_module/file.pure", "model::MyClass"),
                        newSource("/non_empty_module/file.pure", "model::MyClass", "model::MyOtherClass")
                ).build());
        Assert.assertEquals("Conflict for source: /non_empty_module/file.pure", e.getMessage());
    }

    @Test
    public void testWithElement()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);
        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/more_enums.pure", 1, 1, 3, 1, 1);

        ModuleMetadata simpleModule = ModuleMetadata.builder(name).withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration).build();
        simpleModule.forEachElement(e -> Assert.assertEquals(e.getPath(), simpleModule, simpleModule.withElement(e)));

        ModuleMetadata simpleModulePlus = simpleModule.withElement(notInTheModule);
        Assert.assertNotEquals(simpleModule, simpleModulePlus);
        Assert.assertEquals(simpleModule.getElementCount() + 1, simpleModulePlus.getElementCount());
        Assert.assertEquals(simpleModule.getElements().toList().with(notInTheModule).sortThisBy(PackageableElementMetadata::getPath), simpleModulePlus.getElements());

        ConcreteElementMetadata myThirdClassReplacement = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 14, 1, 20, 1, 1);
        ModuleMetadata simpleModuleWithReplacement = simpleModule.withElement(myThirdClassReplacement);
        Assert.assertNotEquals(simpleModule, simpleModuleWithReplacement);
        Assert.assertEquals(simpleModule.getElementCount(), simpleModuleWithReplacement.getElementCount());
        Assert.assertEquals(simpleModule.getElements().collect(PackageableElementMetadata::getPath), simpleModuleWithReplacement.getElements().collect(PackageableElementMetadata::getPath));

        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> simpleModule.withElement(null));
        Assert.assertEquals("element metadata may not be null", e.getMessage());
    }

    @Test
    public void testWithElements()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);
        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/more_enums.pure", 1, 1, 3, 1, 1);

        ModuleMetadata simpleModule = ModuleMetadata.builder(name).withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration).build();
        Assert.assertEquals(simpleModule, simpleModule.withElements(Lists.immutable.empty()));
        Assert.assertEquals(simpleModule, simpleModule.withElements(simpleModule.getElements()));

        ModuleMetadata simpleModulePlus = simpleModule.withElements(notInTheModule, myFirstEnumeration, mySecondEnumeration);
        Assert.assertNotEquals(simpleModule, simpleModulePlus);
        Assert.assertEquals(simpleModule.getElementCount() + 1, simpleModulePlus.getElementCount());
        Assert.assertEquals(simpleModule.getElements().toList().with(notInTheModule).sortThisBy(PackageableElementMetadata::getPath), simpleModulePlus.getElements());

        ConcreteElementMetadata myThirdClassReplacement = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 14, 1, 20, 1, 1);
        ModuleMetadata simpleModuleWithReplacement = simpleModule.withElements(mySimpleClass, myOtherClass, myThirdClassReplacement);
        Assert.assertNotEquals(simpleModule, simpleModuleWithReplacement);
        Assert.assertEquals(simpleModule.getElementCount(), simpleModuleWithReplacement.getElementCount());
        Assert.assertEquals(simpleModule.getElements().collect(PackageableElementMetadata::getPath), simpleModuleWithReplacement.getElements().collect(PackageableElementMetadata::getPath));

        ModuleMetadata simpleModulePlusWithReplacement = simpleModule.withElements(mySimpleClass, myOtherClass, myThirdClassReplacement, notInTheModule);
        Assert.assertNotEquals(simpleModule, simpleModulePlusWithReplacement);
        Assert.assertEquals(simpleModule.getElementCount() + 1, simpleModulePlusWithReplacement.getElementCount());
        Assert.assertEquals(simpleModule.getElements().toList().without(myThirdClass).with(myThirdClassReplacement).with(notInTheModule).sortThisBy(PackageableElementMetadata::getPath), simpleModulePlusWithReplacement.getElements());

        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> simpleModule.withElements(notInTheModule, null, myThirdClassReplacement));
        Assert.assertEquals("element metadata may not be null", e.getMessage());
    }

    @Test
    public void testWithoutElements()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);

        ModuleMetadata baseModule = ModuleMetadata.builder(name).withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration).build();
        Assert.assertSame(baseModule, baseModule.withoutElements());
        Assert.assertSame(baseModule, baseModule.withoutElements(Lists.immutable.empty()));
        Assert.assertEquals(baseModule, baseModule.withoutElements(emd -> false));

        Assert.assertEquals(baseModule, baseModule.withoutElements("model::enums::NotInTheModule", "model::enums::AlsoNotInTheModule"));
        Assert.assertEquals(baseModule, baseModule.withoutElements(Lists.immutable.with("model::enums::NotInTheModule", "model::enums::AlsoNotInTheModule")));
        Assert.assertEquals(baseModule, baseModule.withoutElements(emd -> "model::enums::NotInTheModule".equals(emd.getPath())));

        Assert.assertEquals(
                ModuleMetadata.builder(name).withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToThird, otherToThird).build(),
                baseModule.withoutElements("model::associations::SimpleToOther", "model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration"));
        Assert.assertEquals(
                ModuleMetadata.builder(name).withElements(simpleToOther, otherToThird, myFirstEnumeration, mySecondEnumeration).build(),
                baseModule.withoutElements(Lists.mutable.with("model::classes::MySimpleClass", "model::associations::SimpleToThird", "model::classes::MyOtherClass", "model::classes::MyThirdClass")));
        Assert.assertEquals(
                ModuleMetadata.builder(name).withElements(mySimpleClass, myOtherClass, myThirdClass).build(),
                baseModule.withoutElements(emd -> emd.getPath().contains("::associations::") || emd.getPath().contains("::enums::")));
        Assert.assertEquals(
                ModuleMetadata.builder(name).build(),
                baseModule.withoutElements(emd -> emd.getPath().startsWith("model::")));
    }

    @Test
    public void testWithSources()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);

        SourceMetadata classesSource = newSource("/test_module/model/classes.pure", "model::classes::MySimpleClass", "model::classes::MyOtherClass", "model::classes::MyThirdClass");
        SourceMetadata associationsSource = newSource("/test_module/model/associations.pure", "model::associations::SimpleToOther", "model::associations::SimpleToThird", "model::associations::OtherToThird");
        SourceMetadata enumsSource = newSource("/test_module/model/enums.pure", "model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration");
        SourceMetadata otherEnumsSource = newSource("/test_module/model/other_enums.pure", "model::enums::NotInTheModule");

        ModuleMetadata simpleModule = ModuleMetadata.builder(name)
                .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                .withSources(classesSource, associationsSource, enumsSource)
                .build();
        Assert.assertEquals(simpleModule, simpleModule.withSources(Lists.immutable.empty()));
        Assert.assertEquals(simpleModule, simpleModule.withSources(simpleModule.getSources()));

        ModuleMetadata simpleModulePlus = simpleModule.withSources(otherEnumsSource, enumsSource, associationsSource);
        Assert.assertNotEquals(simpleModule, simpleModulePlus);
        Assert.assertEquals(simpleModule.getSourceCount() + 1, simpleModulePlus.getSourceCount());
        Assert.assertEquals(simpleModule.getSources().toList().with(otherEnumsSource).sortThisBy(SourceMetadata::getSourceId), simpleModulePlus.getSources());

        SourceMetadata enumsSourceReplacement = newSource("/test_module/model/enums.pure", "model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration", "model::enums::MyThirdEnumeration");
        ModuleMetadata simpleModuleWithReplacement = simpleModule.withSources(classesSource, associationsSource, enumsSourceReplacement);
        Assert.assertNotEquals(simpleModule, simpleModuleWithReplacement);
        Assert.assertEquals(simpleModule.getSourceCount(), simpleModuleWithReplacement.getSourceCount());
        Assert.assertEquals(simpleModule.getSources().collect(SourceMetadata::getSourceId), simpleModuleWithReplacement.getSources().collect(SourceMetadata::getSourceId));

        ModuleMetadata simpleModulePlusWithReplacement = simpleModule.withSources(classesSource, associationsSource, enumsSourceReplacement, otherEnumsSource);
        Assert.assertNotEquals(simpleModule, simpleModulePlusWithReplacement);
        Assert.assertEquals(simpleModule.getSourceCount() + 1, simpleModulePlusWithReplacement.getSourceCount());
        Assert.assertEquals(simpleModule.getSources().toList().without(enumsSource).with(enumsSourceReplacement).with(otherEnumsSource).sortThisBy(SourceMetadata::getSourceId), simpleModulePlusWithReplacement.getSources());

        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> simpleModule.withSources(otherEnumsSource, null, associationsSource));
        Assert.assertEquals("source metadata may not be null", e.getMessage());
    }

    @Test
    public void testWithoutSources()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);

        SourceMetadata classesSource = newSource("/test_module/model/classes.pure", "model::classes::MySimpleClass", "model::classes::MyOtherClass", "model::classes::MyThirdClass");
        SourceMetadata associationsSource = newSource("/test_module/model/associations.pure", "model::associations::SimpleToOther", "model::associations::SimpleToThird", "model::associations::OtherToThird");
        SourceMetadata enumsSource = newSource("/test_module/model/enums.pure", "model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration");

        ModuleMetadata baseModule = ModuleMetadata.builder(name)
                .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                .withSources(classesSource, associationsSource, enumsSource)
                .build();
        Assert.assertSame(baseModule, baseModule.withoutSources());
        Assert.assertSame(baseModule, baseModule.withoutSources(Lists.immutable.empty()));
        Assert.assertEquals(baseModule, baseModule.withoutSources(emd -> false));

        Assert.assertEquals(baseModule, baseModule.withoutSources("/test_module/model/other_enums.pure", "/test_module/model/not_in_the_model.pure"));
        Assert.assertEquals(baseModule, baseModule.withoutSources(Lists.immutable.with("/test_module/model/other_enums.pure", "/test_module/model/not_in_the_model.pure")));
        Assert.assertEquals(baseModule, baseModule.withoutSources(smd -> "/test_module/model/not_in_the_model.pure".equals(smd.getSourceId())));

        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                        .withSources(classesSource)
                        .build(),
                baseModule.withoutSources("/test_module/model/associations.pure", "/test_module/model/enums.pure"));
        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                        .withSources(associationsSource)
                        .build(),
                baseModule.withoutSources(Lists.mutable.with("/test_module/model/classes.pure", "/test_module/model/enums.pure")));
        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                        .withSources(classesSource)
                        .build(),
                baseModule.withoutSources(smd -> smd.getSourceId().contains("associations") || smd.getSourceId().contains("enums")));
        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                        .build(),
                baseModule.withoutSources(smd -> smd.getSourceId().startsWith("/test_module")));
    }

    @Test
    public void testUpdate()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1, 1);

        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/more_enums.pure", 1, 1, 3, 1, 1);
        ConcreteElementMetadata myThirdClassReplacement = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 14, 1, 20, 1, 1);

        SourceMetadata classesSource = newSource("/test_module/model/classes.pure", "model::classes::MySimpleClass", "model::classes::MyOtherClass", "model::classes::MyThirdClass");
        SourceMetadata associationsSource = newSource("/test_module/model/associations.pure", "model::associations::SimpleToOther", "model::associations::SimpleToThird", "model::associations::OtherToThird");
        SourceMetadata enumsSource = newSource("/test_module/model/enums.pure", "model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration");
        SourceMetadata moreEnumsSource = newSource("/test_module/model/more_enums.pure", "model::enums::NotInTheModule");

        SourceMetadata classesSourceReplacement = newSource("/test_module/model/classes.pure", "model::classes::MySimpleClass", "model::classes::MyThirdClass");
        SourceMetadata associationsSourceReplacement = newSource("/test_module/model/associations.pure", "model::associations::SimpleToOther", "model::associations::OtherToThird");

        ModuleMetadata baseModule = ModuleMetadata.builder(name)
                .withElements(mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration)
                .withSources(classesSource, associationsSource, enumsSource)
                .build();
        Assert.assertSame(baseModule, baseModule.update(null, (Iterable<String>) null, null, null));
        Assert.assertSame(baseModule, baseModule.update(null, (Predicate<ConcreteElementMetadata>) null, null, null));
        Assert.assertSame(baseModule, baseModule.update(Lists.immutable.empty(), Lists.immutable.empty(), Lists.immutable.empty(), Lists.immutable.empty()));
        Assert.assertEquals(baseModule, baseModule.update(baseModule.getElements(), Lists.immutable.with("model::enums::NotInTheModule"), baseModule.getSources(), Lists.immutable.with("/test_module/model/other_enums.pure")));

        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myThirdClassReplacement, simpleToOther, simpleToThird, otherToThird)
                        .withSources(classesSourceReplacement, associationsSource)
                        .build(),
                baseModule.update(
                        Lists.immutable.with(mySimpleClass, myThirdClassReplacement),
                        Lists.immutable.with("model::enums::MyFirstEnumeration", "model::enums::MySecondEnumeration", "model::classes::MyOtherClass"),
                        Lists.immutable.with(associationsSource, classesSourceReplacement),
                        Lists.immutable.with("/test_module/model/enums.pure")));
        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myThirdClassReplacement, myFirstEnumeration, mySecondEnumeration)
                        .withSources(classesSourceReplacement, enumsSource)
                        .build(),
                baseModule.update(
                        Lists.immutable.with(mySimpleClass, myThirdClassReplacement),
                        emd -> emd.getPath().contains("::associations::") || "model::classes::MyOtherClass".equals(emd.getPath()),
                        Lists.immutable.with(classesSourceReplacement),
                        smd -> smd.getSourceId().contains("associations")));
        Assert.assertEquals(
                ModuleMetadata.builder(name)
                        .withElements(mySimpleClass, myOtherClass, myThirdClassReplacement, simpleToOther, otherToThird, myFirstEnumeration, mySecondEnumeration, notInTheModule)
                        .withSources(classesSource, associationsSourceReplacement, enumsSource, moreEnumsSource)
                        .build(),
                baseModule.update(
                        Lists.immutable.with(myThirdClassReplacement, notInTheModule),
                        Lists.immutable.with("model::associations::SimpleToThird"),
                        Lists.immutable.with(moreEnumsSource, associationsSourceReplacement),
                        Lists.immutable.empty()));

        NullPointerException e1 = Assert.assertThrows(NullPointerException.class, () -> baseModule.update(Lists.immutable.with(notInTheModule, null, myThirdClassReplacement), Lists.immutable.empty(), null, null));
        Assert.assertEquals("element metadata may not be null", e1.getMessage());

        NullPointerException e2 = Assert.assertThrows(NullPointerException.class, () -> baseModule.update(null, null, Lists.immutable.with(moreEnumsSource, null), Lists.immutable.empty()));
        Assert.assertEquals("source metadata may not be null", e2.getMessage());
    }

    private void assertForEachElement(ModuleMetadata module, ConcreteElementMetadata... expectedElements)
    {
        MutableList<ConcreteElementMetadata> actual = Lists.mutable.empty();
        module.forEachElement(actual::add);
        Assert.assertEquals(Arrays.asList(expectedElements), actual);
    }

    private void assertForEachSource(ModuleMetadata module, SourceMetadata... expectedSources)
    {
        MutableList<SourceMetadata> actual = Lists.mutable.empty();
        module.forEachSource(actual::add);
        Assert.assertEquals(Arrays.asList(expectedSources), actual);
    }
}
