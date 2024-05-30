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
import org.eclipse.collections.api.list.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestModuleMetadata extends AbstractMetadataTest
{
    @Test
    public void testEmptyModule()
    {
        String name = "empty_module";
        ModuleMetadata emptyModule = new ModuleMetadata(name);
        Assert.assertEquals(name, emptyModule.getName());
        Assert.assertEquals(0, emptyModule.getElementCount());
        Assert.assertEquals(Lists.immutable.empty(), emptyModule.getElements());
        assertForEachElement(emptyModule);

        Assert.assertEquals(emptyModule, new ModuleMetadata(name));
        Assert.assertNotEquals(emptyModule, new ModuleMetadata(name + "_" + name));
        Assert.assertNotEquals(emptyModule, new ModuleMetadata("non_empty_module", newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1)));
    }

    @Test
    public void testMultiSourceModule()
    {
        String name = "test_module";

        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1);
        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/other_enums.pure", 1, 1, 3, 1);

        ModuleMetadata simpleModule = new ModuleMetadata(name, mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration);
        Assert.assertEquals(name, simpleModule.getName());
        Assert.assertEquals(8, simpleModule.getElementCount());
        Assert.assertEquals(Lists.immutable.with(otherToThird, simpleToOther, simpleToThird, myOtherClass, mySimpleClass, myThirdClass, myFirstEnumeration, mySecondEnumeration), simpleModule.getElements());
        assertForEachElement(simpleModule, otherToThird, simpleToOther, simpleToThird, myOtherClass, mySimpleClass, myThirdClass, myFirstEnumeration, mySecondEnumeration);

        Assert.assertEquals(simpleModule, new ModuleMetadata(name, otherToThird, simpleToOther, simpleToThird, myOtherClass, mySimpleClass, myThirdClass, myFirstEnumeration, mySecondEnumeration));
        Assert.assertEquals(simpleModule, new ModuleMetadata(name, mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration));
        Assert.assertEquals(simpleModule, new ModuleMetadata(name, myFirstEnumeration, mySimpleClass, myOtherClass, simpleToOther, simpleToThird, otherToThird, mySecondEnumeration, myThirdClass));
        Assert.assertNotEquals(simpleModule, new ModuleMetadata(name, mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, mySecondEnumeration));
        Assert.assertNotEquals(simpleModule, new ModuleMetadata(name, mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration, notInTheModule));
    }

    @Test
    public void testInvalidName()
    {
        NullPointerException eNull = Assert.assertThrows(NullPointerException.class, () -> new ModuleMetadata(null));
        Assert.assertEquals("name may not be null", eNull.getMessage());

        IllegalArgumentException eEmpty = Assert.assertThrows(IllegalArgumentException.class, () -> new ModuleMetadata(""));
        Assert.assertEquals("name may not be empty", eEmpty.getMessage());
    }

    @Test
    public void testElementPathConflict()
    {
        // this should work, since the two are equal
        new ModuleMetadata("non_empty_module",
                newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1),
                newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1));

        IllegalArgumentException e = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new ModuleMetadata("test_module",
                        newClass("model::MyClass", "/test_module/file.pure", 1, 1, 5, 1),
                        newAssociation("model::MyAssociation", "/test_module/file.pure", 6, 1, 8, 1),
                        newClass("model::MyClass", "/test_module/file.pure", 9, 1, 15, 1)));
        Assert.assertEquals("Conflict for element: model::MyClass", e.getMessage());
    }

    @Test
    public void testSourceInfoOverlap()
    {
        // this should work, since the two are equal
        new ModuleMetadata("non_empty_module",
                newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1),
                newClass("model::MyClass", "/non_empty_module/file.pure", 1, 1, 5, 1));

        IllegalArgumentException e = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new ModuleMetadata("test_module",
                        newClass("model::MyClass", "/test_module/file.pure", 1, 1, 5, 1),
                        newAssociation("model::MyAssociation", "/test_module/file.pure", 3, 1, 8, 1)));
        Assert.assertEquals("Overlapping source information for model::MyClass (/test_module/file.pure:1c1-5c1) and model::MyAssociation (/test_module/file.pure:3c1-8c1)", e.getMessage());
    }

    @Test
    public void testWithElement()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1);
        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/more_enums.pure", 1, 1, 3, 1);

        ModuleMetadata simpleModule = new ModuleMetadata(name, mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration);
        simpleModule.forEachElement(e -> Assert.assertEquals(e.getPath(), simpleModule, simpleModule.withElement(e)));

        ModuleMetadata simpleModulePlus = simpleModule.withElement(notInTheModule);
        Assert.assertNotEquals(simpleModule, simpleModulePlus);
        Assert.assertEquals(simpleModule.getElementCount() + 1, simpleModulePlus.getElementCount());
        Assert.assertEquals(simpleModule.getElements().toList().with(notInTheModule).sortThisBy(PackageableElementMetadata::getPath), simpleModulePlus.getElements());

        ConcreteElementMetadata myThirdClassReplacement = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 14, 1, 20, 1);
        ModuleMetadata simpleModuleWithReplacement = simpleModule.withElement(myThirdClassReplacement);
        Assert.assertNotEquals(simpleModule, simpleModuleWithReplacement);
        Assert.assertEquals(simpleModule.getElementCount(), simpleModuleWithReplacement.getElementCount());
        Assert.assertEquals(simpleModule.getElements().collect(PackageableElementMetadata::getPath), simpleModuleWithReplacement.getElements().collect(PackageableElementMetadata::getPath));

        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> simpleModule.withElement(null));
        Assert.assertEquals("element may not be null", e.getMessage());
    }

    @Test
    public void testWithElements()
    {
        String name = "test_module";
        ConcreteElementMetadata mySimpleClass = newClass("model::classes::MySimpleClass", "/test_module/model/classes.pure", 1, 1, 5, 1);
        ConcreteElementMetadata myOtherClass = newClass("model::classes::MyOtherClass", "/test_module/model/classes.pure", 6, 1, 10, 1);
        ConcreteElementMetadata myThirdClass = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 12, 1, 20, 1);
        ConcreteElementMetadata simpleToOther = newAssociation("model::associations::SimpleToOther", "/test_module/model/associations.pure", 2, 1, 7, 1);
        ConcreteElementMetadata simpleToThird = newAssociation("model::associations::SimpleToThird", "/test_module/model/associations.pure", 9, 1, 16, 1);
        ConcreteElementMetadata otherToThird = newAssociation("model::associations::OtherToThird", "/test_module/model/associations.pure", 18, 1, 25, 1);
        ConcreteElementMetadata myFirstEnumeration = newEnumeration("model::enums::MyFirstEnumeration", "/test_module/model/enums.pure", 3, 1, 6, 1);
        ConcreteElementMetadata mySecondEnumeration = newEnumeration("model::enums::MySecondEnumeration", "/test_module/model/enums.pure", 8, 1, 10, 1);
        ConcreteElementMetadata notInTheModule = newEnumeration("model::enums::NotInTheModule", "/test_module/model/more_enums.pure", 1, 1, 3, 1);

        ModuleMetadata simpleModule = new ModuleMetadata(name, mySimpleClass, myOtherClass, myThirdClass, simpleToOther, simpleToThird, otherToThird, myFirstEnumeration, mySecondEnumeration);
        Assert.assertEquals(simpleModule, simpleModule.withElements(simpleModule.getElements()));

        ModuleMetadata simpleModulePlus = simpleModule.withElements(notInTheModule, myFirstEnumeration, mySecondEnumeration);
        Assert.assertNotEquals(simpleModule, simpleModulePlus);
        Assert.assertEquals(simpleModule.getElementCount() + 1, simpleModulePlus.getElementCount());
        Assert.assertEquals(simpleModule.getElements().toList().with(notInTheModule).sortThisBy(PackageableElementMetadata::getPath), simpleModulePlus.getElements());

        ConcreteElementMetadata myThirdClassReplacement = newClass("model::classes::MyThirdClass", "/test_module/model/classes.pure", 14, 1, 20, 1);
        ModuleMetadata simpleModuleWithReplacement = simpleModule.withElements(mySimpleClass, myOtherClass, myThirdClassReplacement);
        Assert.assertNotEquals(simpleModule, simpleModuleWithReplacement);
        Assert.assertEquals(simpleModule.getElementCount(), simpleModuleWithReplacement.getElementCount());
        Assert.assertEquals(simpleModule.getElements().collect(PackageableElementMetadata::getPath), simpleModuleWithReplacement.getElements().collect(PackageableElementMetadata::getPath));

        ModuleMetadata simpleModulePlusWithReplacement = simpleModule.withElements(mySimpleClass, myOtherClass, myThirdClassReplacement, notInTheModule);
        Assert.assertNotEquals(simpleModule, simpleModulePlusWithReplacement);
        Assert.assertEquals(simpleModule.getElementCount() + 1, simpleModulePlusWithReplacement.getElementCount());
        Assert.assertEquals(simpleModule.getElements().toList().without(myThirdClass).with(myThirdClassReplacement).with(notInTheModule).sortThisBy(PackageableElementMetadata::getPath), simpleModulePlusWithReplacement.getElements());

        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> simpleModule.withElements(notInTheModule, null, myThirdClassReplacement));
        Assert.assertEquals("element may not be null", e.getMessage());
    }

    private void assertForEachElement(ModuleMetadata module, ConcreteElementMetadata... expectedElements)
    {
        MutableList<ConcreteElementMetadata> actual = Lists.mutable.empty();
        module.forEachElement(actual::add);
        Assert.assertEquals(Arrays.asList(expectedElements), actual);
    }
}
