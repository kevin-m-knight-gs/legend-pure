package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.list.ListIterable;

public interface ObjOrUpdate
{
    String getIdentifier();
    String getClassifier();
    ListIterable<PropertyValue> getPropertyValues();
    <T> T visit(ObjOrUpdateVisitor<T> visitor);
}
