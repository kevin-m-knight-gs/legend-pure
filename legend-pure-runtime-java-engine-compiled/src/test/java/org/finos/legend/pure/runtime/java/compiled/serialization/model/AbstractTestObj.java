package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

abstract class AbstractTestObj<T extends Obj>
{
    @Test
    public void testEmptyUpdate()
    {
        Obj original = newObjForUpdateTests();
        Obj copy = original.cloneWithNewPropertyValues(Lists.immutable.empty());
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        // no updates
        Assert.assertSame(original, original.applyUpdates());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        // updates with no new values
        Assert.assertSame(original, original.applyUpdates(
                newObjUpdate(original.getIdentifier(), original.getClassifier()), // no properties at all
                newObjUpdate(original.getIdentifier(), original.getClassifier(), newPropertyValue("prop")))); // property with no values
        Assert.assertEquals(copy, original); // assert that the original has not changed
    }

    @Test
    public void testInvalidUpdate()
    {
        Obj original = newObjForUpdateTests();
        Obj copy = original.cloneWithNewPropertyValues(Lists.immutable.empty());
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        String identifier = original.getIdentifier();
        String classifier = original.getClassifier();
        String otherIdentifier = original.getIdentifier() + "_Other";
        String otherClassifier = original.getClassifier() + "_Other";

        IllegalArgumentException e1 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(otherIdentifier, classifier)));
        Assert.assertEquals("Cannot apply update for " + otherIdentifier + " (classifier: " + classifier + ") to " + identifier + " (classifier: " + classifier + ")", e1.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        IllegalArgumentException e2 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(identifier, otherClassifier)));
        Assert.assertEquals("Cannot apply update for " + identifier + " (classifier: " + otherClassifier + ") to " + identifier + " (classifier: " + classifier + ")", e2.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        IllegalArgumentException e3 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(otherIdentifier, otherClassifier)));
        Assert.assertEquals("Cannot apply update for " + otherIdentifier + " (classifier: " + otherClassifier + ") to " + identifier + " (classifier: " + classifier + ")", e3.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed

        IllegalArgumentException e4 = Assert.assertThrows(IllegalArgumentException.class, () -> original.applyUpdates(newObjUpdate(identifier, classifier), newObjUpdate(otherIdentifier, otherClassifier)));
        Assert.assertEquals("Cannot apply update for " + otherIdentifier + " (classifier: " + otherClassifier + ") to " + identifier + " (classifier: " + classifier + ")", e4.getMessage());
        Assert.assertEquals(copy, original); // assert that the original has not changed
    }

    @Test
    public void testSingleUpdate()
    {
        String property1 = "prop1";
        String property2 = "prop2";
        Obj original = newObjForUpdateTests(newPrimitivePropertyValue(property1, "a", "b"));
        Obj copy = original.cloneWithNewPropertyValues(Lists.immutable.with(newPrimitivePropertyValue(property1, "a", "b")));
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        String identifier = original.getIdentifier();
        String classifier = original.getClassifier();

        Obj update = original.applyUpdates(newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "c", "d"), newPrimitivePropertyValue(property2, 7)));
        Assert.assertEquals(copy, original); // assert that the original has not changed
        Assert.assertNotEquals(original, update);

        Assert.assertSame(getObjClass(), update.getClass());
        Assert.assertSame(original.getClass(), update.getClass());
        Assert.assertEquals(original.getSourceInformation(), update.getSourceInformation());
        Assert.assertEquals(identifier, update.getIdentifier());
        Assert.assertEquals(classifier, update.getClassifier());
        Assert.assertEquals(original.getName(), update.getName());
        Assert.assertEquals(
                Lists.mutable.with(newPrimitivePropertyValue(property1, "a", "b", "c", "d"), newPrimitivePropertyValue(property2, 7)),
                update.getPropertyValues());
    }

    @Test
    public void testMultiUpdate()
    {
        String property1 = "prop1";
        String property2 = "prop2";
        String property3 = "prop3";
        Obj original = newObjForUpdateTests(newPrimitivePropertyValue(property1, "a", "b"));
        Obj copy = original.cloneWithNewPropertyValues(Lists.immutable.with(newPrimitivePropertyValue(property1, "a", "b")));
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);
        Assert.assertEquals(copy, original);

        String identifier = original.getIdentifier();
        String classifier = original.getClassifier();

        Obj update = original.applyUpdates(
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "c"), newPrimitivePropertyValue(property2, 7)),
                newObjUpdate(identifier, classifier, newPrimitivePropertyValue(property1, "d", "e", "f", "g"), newPrimitivePropertyValue(property3, true, false)));
        Assert.assertEquals(copy, original); // assert that the original has not changed
        Assert.assertNotEquals(original, update);

        Assert.assertSame(getObjClass(), update.getClass());
        Assert.assertSame(original.getClass(), update.getClass());
        Assert.assertEquals(original.getSourceInformation(), update.getSourceInformation());
        Assert.assertEquals(identifier, update.getIdentifier());
        Assert.assertEquals(classifier, update.getClassifier());
        Assert.assertEquals(original.getName(), update.getName());
        Assert.assertEquals(
                Lists.mutable.with(
                        newPrimitivePropertyValue(property1, "a", "b", "c", "d", "e", "f", "g"),
                        newPrimitivePropertyValue(property2, 7),
                        newPrimitivePropertyValue(property3, true, false)),
                update.getPropertyValues());
    }

    protected abstract Class<T> getObjClass();

    protected T newObjForUpdateTests(PropertyValue... propertiesValues)
    {
        return newObjForUpdateTests(ArrayAdapter.adapt(propertiesValues).asUnmodifiable());
    }

    protected abstract T newObjForUpdateTests(ListIterable<PropertyValue> propertiesValues);

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
