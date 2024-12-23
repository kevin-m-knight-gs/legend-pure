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

package org.finos.legend.pure.m3.serialization.compiler.reference.v1;

import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

class ReferenceIdProviderV1 implements ReferenceIdProvider
{
    private final ContainingElementIndex containingElementIndex;
    private final ReferenceIdGenerator idGenerator;
    private final ConcurrentMutableMap<CoreInstance, MapIterable<CoreInstance, String>> idCache = ConcurrentHashMap.newMap();

    ReferenceIdProviderV1(ContainingElementIndex containingElementIndex, ReferenceIdGenerator idGenerator)
    {
        this.containingElementIndex = containingElementIndex;
        this.idGenerator = idGenerator;
    }

    ReferenceIdProviderV1(ProcessorSupport processorSupport)
    {
        this(ContainingElementIndex.builder(processorSupport).withAllElements().build(), new ReferenceIdGenerator(processorSupport));
    }

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public String getReferenceId(CoreInstance reference)
    {
        CoreInstance owner = this.containingElementIndex.findContainingElement(reference);
        if (owner != null)
        {
            String id = this.idCache.getIfAbsentPutWithKey(owner, this.idGenerator::generateIdsForElement).get(reference);
            if (id != null)
            {
                return id;
            }
        }

        // Cannot get reference id
        StringBuilder builder = new StringBuilder("Cannot get a reference id for instance");
        SourceInformation sourceInfo = reference.getSourceInformation();
        if (sourceInfo == null)
        {
            builder.append(" with no source information");
        }
        else
        {
            sourceInfo.appendMessage(builder.append(" at "));
        }
        if (owner == null)
        {
            builder.append(" (no containing element)");
        }
        else
        {
            PackageableElement.writeUserPathForPackageableElement(builder.append(" contained in "), owner);
        }
        throw new IllegalArgumentException(builder.append(": ").append(reference).toString());
    }
}
