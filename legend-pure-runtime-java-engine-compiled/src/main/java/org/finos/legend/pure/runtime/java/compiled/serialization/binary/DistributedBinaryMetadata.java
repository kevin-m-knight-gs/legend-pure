package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import java.util.Collection;
import java.util.Collections;

public interface DistributedBinaryMetadata
{
    String getName();

    default Collection<String> getDependencies()
    {
        return Collections.emptyList();
    }
}
