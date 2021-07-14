package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

public class TestObj extends AbstractTestObj<Obj>
{
    @Override
    protected Class<Obj> getObjClass()
    {
        return Obj.class;
    }

    @Override
    protected Obj newObjForUpdateTests(ListIterable<PropertyValue> propertiesValues)
    {
        return new Obj(
                new SourceInformation("source1.pure", 1, 2, 3, 4, 5, 6),
                "test::SomeId",
                "meta::pure::SomeClassifier",
                "SomeId",
                propertiesValues
        );
    }
}
