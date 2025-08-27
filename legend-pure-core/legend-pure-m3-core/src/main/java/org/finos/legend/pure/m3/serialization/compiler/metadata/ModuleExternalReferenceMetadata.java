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

import java.util.Objects;

public class ModuleExternalReferenceMetadata
{
    private final String name;
    private final ImmutableList<ElementExternalReferenceMetadata> elementExternalReferences;

    private ModuleExternalReferenceMetadata(String name, ImmutableList<ElementExternalReferenceMetadata> elementExternalReferences)
    {
        this.name = name;
        this.elementExternalReferences = elementExternalReferences;
    }

    public String getModuleName()
    {
        return this.name;
    }

    public ImmutableList<ElementExternalReferenceMetadata> getExternalReferences()
    {
        return this.elementExternalReferences;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ModuleExternalReferenceMetadata))
        {
            return false;
        }

        ModuleExternalReferenceMetadata that = (ModuleExternalReferenceMetadata) other;
        return this.name.equals(that.name) && this.elementExternalReferences.equals(that.elementExternalReferences);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    public String toString()
    {
        return appendString(new StringBuilder("<").append(getClass().getSimpleName()).append(' ')).append('>').toString();
    }

    StringBuilder appendString(StringBuilder builder)
    {
        builder.append("moduleName='").append(this.name).append("' extRefs=[");
        if (this.elementExternalReferences.notEmpty())
        {
            this.elementExternalReferences.forEach(xr -> xr.appendString(builder.append('{')).append("}, "));
            builder.setLength(builder.length() - 2);
        }
        return builder.append(']');
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(int elementExternalReferenceCount)
    {
        return new Builder(elementExternalReferenceCount);
    }

    public static Builder builder(ModuleExternalReferenceMetadata metadata)
    {
        return new Builder(metadata);
    }

    public static class Builder
    {
        private String name;
        private final MutableList<ElementExternalReferenceMetadata> elementExternalReferences;

        private Builder()
        {
            this.elementExternalReferences = Lists.mutable.empty();
        }

        private Builder(int elementExternalReferenceCount)
        {
            this.elementExternalReferences = Lists.mutable.withInitialCapacity(elementExternalReferenceCount);
        }

        private Builder(ModuleExternalReferenceMetadata metadata)
        {
            this.name = metadata.name;
            this.elementExternalReferences = Lists.mutable.withAll(metadata.elementExternalReferences);
        }

        public void setModuleName(String name)
        {
            this.name = name;
        }

        public void addElementExternalReferenceMetadata(ElementExternalReferenceMetadata elementExternalReference)
        {
            this.elementExternalReferences.add(Objects.requireNonNull(elementExternalReference));
        }

        public Builder withModuleName(String name)
        {
            setModuleName(name);
            return this;
        }

        public Builder withElementExternalReferenceMetadata(ElementExternalReferenceMetadata elementExternalReference)
        {
            addElementExternalReferenceMetadata(elementExternalReference);
            return this;
        }

        public ModuleExternalReferenceMetadata build()
        {
            return new ModuleExternalReferenceMetadata(Objects.requireNonNull(this.name, "module name may not be null"), buildElementExternalReferences());
        }

        private ImmutableList<ElementExternalReferenceMetadata> buildElementExternalReferences()
        {
            if (this.elementExternalReferences.size() > 1)
            {
                this.elementExternalReferences.sortThisBy(ElementExternalReferenceMetadata::getElementPath);
                int index = 0;
                while (index < this.elementExternalReferences.size())
                {
                    int start = index++;
                    ElementExternalReferenceMetadata current = this.elementExternalReferences.get(start);
                    String currentPath = current.getElementPath();
                    while ((index < this.elementExternalReferences.size()) && currentPath.equals(this.elementExternalReferences.get(index).getElementPath()))
                    {
                        index++;
                    }
                    if (index > start + 1)
                    {
                        // Multiple ElementExternalReferenceMetadata objects for the same element: merge them
                        ElementExternalReferenceMetadata.Builder builder = ElementExternalReferenceMetadata.builder(current);
                        for (int i = start + 1; i < index; i++)
                        {
                            // merge and set to null to mark for removal
                            builder.addMetadata(this.elementExternalReferences.set(i, null));
                        }
                        this.elementExternalReferences.set(start, builder.build());
                    }
                }
                this.elementExternalReferences.removeIf(Objects::isNull);
            }
            return this.elementExternalReferences.toImmutable();
        }
    }
}
