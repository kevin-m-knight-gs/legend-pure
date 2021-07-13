package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestObjUpdate
{
    @Test
    public void testEmptyUpdate()
    {
        SourceInformation sourceInfo = new SourceInformation("source1.pure", 1, 2, 3, 4, 5, 6);
        String identifier = "test::SomeId";
        String classifier = "meta::pure::SomeClassifier";
        String name = "SomeId";
        Obj original = newObj(sourceInfo, identifier, classifier, name);
        Obj copy = newObj(sourceInfo, identifier, classifier, name);
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        // no updates
        Assert.assertSame(original, original.applyUpdates());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        // updates with no new values
        Assert.assertSame(original, original.applyUpdates(
                newObjUpdate(identifier, classifier), // no properties at all
                newObjUpdate(identifier, classifier, newPropertyValue("prop")))); // property with no values
        Assert.assertEquals(copy, original); // assert that the original has not changed
    }

    @Test
    public void testInvalidUpdate()
    {
        SourceInformation sourceInfo = new SourceInformation("source2.pure", 6, 5, 4, 3, 2, 1);
        String identifier = "test::SomeId";
        String classifier = "meta::pure::SomeClassifier";
        String name = "SomeId";
        String otherIdentifier = "test::SomeOtherId";
        String otherClassifier = "meta::pure::SomeOtherClassifier";

        Obj original = newObj(sourceInfo, identifier, classifier, name);
        Obj copy = newObj(sourceInfo, identifier, classifier, name);
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        IllegalArgumentException e1 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(otherIdentifier, classifier)));
        Assert.assertEquals("Cannot apply update for test::SomeOtherId (classifier: meta::pure::SomeClassifier) to test::SomeId (classifier: meta::pure::SomeClassifier)", e1.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        IllegalArgumentException e2 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(identifier, otherClassifier)));
        Assert.assertEquals("Cannot apply update for test::SomeId (classifier: meta::pure::SomeOtherClassifier) to test::SomeId (classifier: meta::pure::SomeClassifier)", e2.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        IllegalArgumentException e3 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(otherIdentifier, otherClassifier)));
        Assert.assertEquals("Cannot apply update for test::SomeOtherId (classifier: meta::pure::SomeOtherClassifier) to test::SomeId (classifier: meta::pure::SomeClassifier)", e3.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        IllegalArgumentException e4 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(identifier, classifier), newObjUpdate(otherIdentifier, otherClassifier)));
        Assert.assertEquals("Cannot apply update for test::SomeOtherId (classifier: meta::pure::SomeOtherClassifier) to test::SomeId (classifier: meta::pure::SomeClassifier)", e4.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed
    }

    @Test
    public void testObjSingleUpdate()
    {
        SourceInformation sourceInfo = new SourceInformation("source3.pure", 1, 6, 2, 5, 3, 4);
        String identifier = "test::SomeId";
        String classifier = "meta::pure::SomeClassifier";
        String name = "SomeId";
        String property1 = "prop1";
        String property2 = "prop2";
        Obj original = newObj(sourceInfo, identifier, classifier, name, newPrimitivePropertyValue(property1, "a", "b"));
        Obj copy = newObj(sourceInfo, identifier, classifier, name, newPrimitivePropertyValue(property1, "a", "b"));
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        Obj update = original.applyUpdates(newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "c", "d"), newPrimitivePropertyValue(property2, 7)));
        Assert.assertEquals(copy, original); // assert that the original has not changed
        Assert.assertNotEquals(original, update);

        Assert.assertSame(Obj.class, update.getClass());
        Assert.assertSame(original.getClass(), update.getClass());
        Assert.assertEquals(sourceInfo, update.getSourceInformation());
        Assert.assertEquals(identifier, update.getIdentifier());
        Assert.assertEquals(classifier, update.getClassifier());
        Assert.assertEquals(name, update.getName());
        Assert.assertEquals(
                Lists.mutable.with(newPrimitivePropertyValue(property1, "a", "b", "c", "d"), newPrimitivePropertyValue(property2, 7)),
                update.getPropertyValues());
    }

    @Test
    public void testObjMultiUpdate()
    {
        SourceInformation sourceInfo = new SourceInformation("source4.pure", 2, 3, 4, 5, 6, 1);
        String identifier = "test::SomeId";
        String classifier = "meta::pure::SomeClassifier";
        String name = "SomeId";
        String property1 = "prop1";
        String property2 = "prop2";
        String property3 = "prop3";
        Obj original = newObj(sourceInfo, identifier, classifier, name, newPrimitivePropertyValue(property1, "a", "b"));
        Obj copy = newObj(sourceInfo, identifier, classifier, name, newPrimitivePropertyValue(property1, "a", "b"));
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        Obj update = original.applyUpdates(
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "c"), newPrimitivePropertyValue(property2, 7)),
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "d", "e", "f", "g"), newPrimitivePropertyValue(property3, true, false)));
        Assert.assertEquals(copy, original); // assert that the original has not changed
        Assert.assertNotEquals(original, update);

        Assert.assertSame(Obj.class, update.getClass());
        Assert.assertSame(original.getClass(), update.getClass());
        Assert.assertEquals(sourceInfo, update.getSourceInformation());
        Assert.assertEquals(identifier, update.getIdentifier());
        Assert.assertEquals(classifier, update.getClassifier());
        Assert.assertEquals(name, update.getName());
        Assert.assertEquals(
                Lists.mutable.with(
                        newPrimitivePropertyValue(property1, "a", "b", "c", "d", "e", "f", "g"),
                        newPrimitivePropertyValue(property2, 7),
                        newPrimitivePropertyValue(property3, true, false)),
                update.getPropertyValues());
    }

    @Test
    public void testEnumUpdate()
    {
        SourceInformation sourceInfo = new SourceInformation("source5.pure", 2, 4, 6, 1, 3, 5);
        String identifier = "test::SomeEnum.EnumName";
        String classifier = "meta::pure::SomeEnumeration";
        String name = "VAL1";
        String property1 = "prop1";
        String property2 = "prop2";
        String property3 = "prop3";
        Enum original = newEnum(sourceInfo, identifier, classifier, name, newPrimitivePropertyValue(property1, "a", "b"));
        Enum copy = newEnum(sourceInfo, identifier, classifier, name, newPrimitivePropertyValue(property1, "a", "b"));
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        Obj update = original.applyUpdates(
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "c"), newPrimitivePropertyValue(property2, 7)),
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "d", "e", "f", "g"), newPrimitivePropertyValue(property3, true, false)));
        Assert.assertEquals(copy, original); // assert that the original has not changed
        Assert.assertNotEquals(original, update);

        Assert.assertSame(Enum.class, update.getClass());
        Assert.assertSame(original.getClass(), update.getClass());
        Assert.assertEquals(sourceInfo, update.getSourceInformation());
        Assert.assertEquals(identifier, update.getIdentifier());
        Assert.assertEquals(classifier, update.getClassifier());
        Assert.assertEquals(name, update.getName());
        Assert.assertEquals(
                Lists.mutable.with(
                        newPrimitivePropertyValue(property1, "a", "b", "c", "d", "e", "f", "g"),
                        newPrimitivePropertyValue(property2, 7),
                        newPrimitivePropertyValue(property3, true, false)),
                update.getPropertyValues());
    }

    @Test
    public void testObjUpdateUpdate()
    {
        String identifier = "test::SomeId";
        String classifier = "meta::pure::SomeClassifier";
        String property1 = "prop1";
        String property2 = "prop2";
        String property3 = "prop3";
        ObjUpdate original = newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "a", "b"));
        ObjUpdate copy = newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "a", "b"));
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        Obj update = original.applyUpdates(
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "c"), newPrimitivePropertyValue(property2, 7)),
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "d", "e", "f", "g"), newPrimitivePropertyValue(property3, true, false)));
        Assert.assertEquals(copy, original); // assert that the original has not changed
        Assert.assertNotEquals(original, update);

        Assert.assertSame(ObjUpdate.class, update.getClass());
        Assert.assertSame(original.getClass(), update.getClass());
        Assert.assertNull(update.getSourceInformation());
        Assert.assertEquals(identifier, update.getIdentifier());
        Assert.assertEquals(classifier, update.getClassifier());
        Assert.assertNull(update.getName());
        Assert.assertEquals(
                Lists.mutable.with(
                        newPrimitivePropertyValue(property1, "a", "b", "c", "d", "e", "f", "g"),
                        newPrimitivePropertyValue(property2, 7),
                        newPrimitivePropertyValue(property3, true, false)),
                update.getPropertyValues());
    }

    private Obj newObj(SourceInformation sourceInformation, String identifier, String classifier, String name, PropertyValue... propertiesValues)
    {
        return new Obj(sourceInformation, identifier, classifier, name, ArrayAdapter.adapt(propertiesValues).asUnmodifiable());
    }

    private Enum newEnum(SourceInformation sourceInformation, String identifier, String classifier, String name, PropertyValue... propertiesValues)
    {
        return new Enum(sourceInformation, identifier, classifier, name, ArrayAdapter.adapt(propertiesValues).asUnmodifiable());
    }

    private ObjUpdate newObjUpdate(String identifier, String classifier, PropertyValue... propertyValues)
    {
        return new ObjUpdate(identifier, classifier, ArrayAdapter.adapt(propertyValues).asUnmodifiable());
    }

    private PropertyValue newPrimitivePropertyValue(String property, Object... values)
    {
        return newPropertyValue(property, Arrays.stream(values).map(Primitive::new).toArray(RValue[]::new));
    }

    private PropertyValue newPropertyValue(String property, RValue... values)
    {
        return (values.length == 1) ? new PropertyValueOne(property, values[0]) : new PropertyValueMany(property, ArrayAdapter.adapt(values).asUnmodifiable());
    }
}
