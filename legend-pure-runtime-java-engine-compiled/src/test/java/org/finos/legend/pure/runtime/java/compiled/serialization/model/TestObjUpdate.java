package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.list.ListIterable;

public class TestObjUpdate extends AbstractTestObj<ObjUpdate>
{
    @Override
    protected Class<ObjUpdate> getObjClass()
    {
        return ObjUpdate.class;
    }

    @Override
    protected ObjUpdate newObjForUpdateTests(ListIterable<PropertyValue> propertiesValues)
    {
        return new ObjUpdate(
                "test::SomeId",
                "meta::pure::SomeClassifier",
                propertiesValues
        );
    }
}
