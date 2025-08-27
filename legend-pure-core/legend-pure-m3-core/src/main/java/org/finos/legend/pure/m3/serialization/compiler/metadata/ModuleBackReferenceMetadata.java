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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.util.Comparator;
import java.util.Objects;

public class ModuleBackReferenceMetadata
{
    private final String name;
    private final ImmutableList<ElementBackReferenceMetadata> elementBackReferences;

    private ModuleBackReferenceMetadata(String name, ImmutableList<ElementBackReferenceMetadata> elementBackReferences)
    {
        this.name = name;
        this.elementBackReferences = elementBackReferences;
    }

    public String getModuleName()
    {
        return this.name;
    }

    public ImmutableList<ElementBackReferenceMetadata> getBackReferences()
    {
        return this.elementBackReferences;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ModuleBackReferenceMetadata))
        {
            return false;
        }

        ModuleBackReferenceMetadata that = (ModuleBackReferenceMetadata) other;
        return this.name.equals(that.name) && this.elementBackReferences.equals(that.elementBackReferences);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    @Override
    public String toString()
    {
        return appendString(new StringBuilder("<").append(getClass().getSimpleName()).append(' ')).append('>').toString();
    }

    StringBuilder appendString(StringBuilder builder)
    {
        builder.append("moduleName='").append(this.name).append("' backRefs=[");
        if (this.elementBackReferences.notEmpty())
        {
            this.elementBackReferences.forEach(xr -> xr.appendString(builder.append('{')).append("}, "));
            builder.setLength(builder.length() - 2);
        }
        return builder.append(']');
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(int elementCount)
    {
        return new Builder(elementCount);
    }

    public static Builder builder(ModuleBackReferenceMetadata metadata)
    {
        return new Builder(metadata);
    }

    public static class Builder
    {
        private String name;
        private final MutableList<ElementBackReferenceMetadata> elementBackReferences;

        private Builder()
        {
            this.elementBackReferences = Lists.mutable.empty();
        }

        private Builder(int elementCount)
        {
            this.elementBackReferences = Lists.mutable.withInitialCapacity(elementCount);
        }

        private Builder(ModuleBackReferenceMetadata metadata)
        {
            this.name = metadata.name;
            this.elementBackReferences = Lists.mutable.withAll(metadata.elementBackReferences);
        }

        public void setModuleName(String name)
        {
            this.name = name;
        }

        public void addElementBackReferenceMetadata(ElementBackReferenceMetadata elementBackReference)
        {
            this.elementBackReferences.add(Objects.requireNonNull(elementBackReference, "element back reference metadata may not be null"));
        }

        public Builder withModuleName(String name)
        {
            setModuleName(name);
            return this;
        }

        public Builder withElementBackReferenceMetadata(ElementBackReferenceMetadata elementBackReference)
        {
            addElementBackReferenceMetadata(elementBackReference);
            return this;
        }

        public ModuleBackReferenceMetadata build()
        {
            return new ModuleBackReferenceMetadata(Objects.requireNonNull(this.name, "name may not be null"), buildElementBackReferences());
        }

        private ImmutableList<ElementBackReferenceMetadata> buildElementBackReferences()
        {
            if (this.elementBackReferences.size() > 1)
            {
                this.elementBackReferences.sort(Comparator.comparing(ElementBackReferenceMetadata::getElementPath));
                int index = 0;
                while (index < this.elementBackReferences.size())
                {
                    int start = index++;
                    ElementBackReferenceMetadata current = this.elementBackReferences.get(start);
                    String currentPath = current.getElementPath();
                    while ((index < this.elementBackReferences.size()) && currentPath.equals(this.elementBackReferences.get(index).getElementPath()))
                    {
                        index++;
                    }
                    if (index > start + 1)
                    {
                        // Multiple InstanceBackReferenceMetadata objects for the same reference: merge them
                        ElementBackReferenceMetadata.Builder builder = ElementBackReferenceMetadata.builder(current);
                        for (int i = start + 1; i < index; i++)
                        {
                            // merge back refs and set to null to mark for removal
                            builder.addMetadata(this.elementBackReferences.set(i, null));
                        }
                        this.elementBackReferences.set(start, builder.build());
                    }
                }
                this.elementBackReferences.removeIf(Objects::isNull);
            }
            return this.elementBackReferences.toImmutable();
        }
    }
}
