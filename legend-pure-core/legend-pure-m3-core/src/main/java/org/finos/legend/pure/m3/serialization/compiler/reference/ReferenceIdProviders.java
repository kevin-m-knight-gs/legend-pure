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

package org.finos.legend.pure.m3.serialization.compiler.reference;

import org.finos.legend.pure.m3.navigation.M3ProcessorSupport;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m4.ModelRepository;

public class ReferenceIdProviders
{
    public static ReferenceIdProvider fromProcessorSupport(ProcessorSupport processorSupport)
    {
        return fromProcessorSupport(processorSupport, true);
    }

    public static ReferenceIdProvider fromProcessorSupport(ProcessorSupport processorSupport, boolean cache)
    {
        return build(ContainingElementIndexes.fromProcessorSupport(processorSupport), new ReferenceIdGenerator(processorSupport), cache);
    }

    public static ReferenceIdProvider fromModelRepository(ModelRepository repository)
    {
        return fromModelRepository(repository, true);
    }

    public static ReferenceIdProvider fromModelRepository(ModelRepository repository, boolean cache)
    {
        return build(ContainingElementIndexes.fromModelRepository(repository), new ReferenceIdGenerator(new M3ProcessorSupport(repository)), cache);
    }

    private static ReferenceIdProvider build(ContainingElementIndex containingElementIndex, ReferenceIdGenerator idGenerator, boolean cache)
    {
        return cache ? new CachedReferenceIdProvider(containingElementIndex, idGenerator) : new SimpleReferenceIdProvider(containingElementIndex, idGenerator);
    }
}
