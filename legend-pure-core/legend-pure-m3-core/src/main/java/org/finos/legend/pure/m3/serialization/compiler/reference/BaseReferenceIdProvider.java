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

import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

abstract class BaseReferenceIdProvider implements ReferenceIdProvider
{
    private final ContainingElementIndex containingElementIndex;

    protected BaseReferenceIdProvider(ContainingElementIndex containingElementIndex)
    {
        this.containingElementIndex = containingElementIndex;
    }

    @Override
    public String getReferenceId(CoreInstance reference)
    {
        CoreInstance owner = this.containingElementIndex.findContainingElement(reference);
        if (owner != null)
        {
            String id = getReferenceId(reference, owner);
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

    protected abstract String getReferenceId(CoreInstance reference, CoreInstance owner);
}
