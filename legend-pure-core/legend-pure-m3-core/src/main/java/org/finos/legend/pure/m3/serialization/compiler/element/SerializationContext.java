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

package org.finos.legend.pure.m3.serialization.compiler.element;

import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringIndexer;

public class SerializationContext
{
    private final StringIndexer stringIndexer;
    private final ReferenceIdProvider referenceIdProvider;
    private final ProcessorSupport processorSupport;

    SerializationContext(StringIndexer stringIndexer, ReferenceIdProvider referenceIdProvider, ProcessorSupport processorSupport)
    {
        this.stringIndexer = stringIndexer;
        this.referenceIdProvider = referenceIdProvider;
        this.processorSupport = processorSupport;
    }

    public StringIndexer getStringIndexer()
    {
        return this.stringIndexer;
    }

    public ReferenceIdProvider getReferenceIdProvider()
    {
        return this.referenceIdProvider;
    }

    public ProcessorSupport getProcessorSupport()
    {
        return this.processorSupport;
    }
}
