package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

public class TestEnum extends AbstractTestObj<Enum>
{
    @Override
    protected Class<Enum> getObjClass()
    {
        return Enum.class;
    }

    @Override
    protected Enum newObjForUpdateTests(ListIterable<PropertyValue> propertiesValues)
    {
        return new Enum(
                new SourceInformation("source2.pure", 6, 5, 4, 3, 2, 1),
                "test::SomeEnum.VAL1",
                "meta::pure::SomeEnumeration",
                "VAL1",
                propertiesValues
        );
    }
}
