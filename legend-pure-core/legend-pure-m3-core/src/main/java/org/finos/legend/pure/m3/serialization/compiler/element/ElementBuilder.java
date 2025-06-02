// Copyright 2025 Goldman Sachs
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

import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.VirtualPackageMetadata;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIds;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.util.function.Supplier;

public interface ElementBuilder
{
    CoreInstance buildVirtualPackage(VirtualPackageMetadata metadata, MetadataIndex index, ReferenceIds referenceIds);

    CoreInstance buildConcreteElement(ConcreteElementMetadata metadata, MetadataIndex index, ReferenceIds referenceIds, Supplier<? extends DeserializedConcreteElement> deserializer);
}
