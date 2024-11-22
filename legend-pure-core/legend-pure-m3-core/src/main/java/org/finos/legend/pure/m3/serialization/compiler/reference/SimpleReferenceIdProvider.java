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

import org.finos.legend.pure.m4.coreinstance.CoreInstance;

class SimpleReferenceIdProvider extends BaseReferenceIdProvider
{
    private final ReferenceIdGenerator idGenerator;

    SimpleReferenceIdProvider(ContainingElementIndex containingElementIndex, ReferenceIdGenerator idGenerator)
    {
        super(containingElementIndex);
        this.idGenerator = idGenerator;
    }

    @Override
    protected String getReferenceId(CoreInstance reference, CoreInstance owner)
    {
        return this.idGenerator.generateIdsForElement(owner).get(reference);
    }
}
