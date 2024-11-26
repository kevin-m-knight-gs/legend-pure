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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.util.Objects;

public class ConcreteElementMetadata extends PackageableElementMetadata
{
    private final SourceInformation sourceInfo;
    private final ImmutableList<String> externalReferences;

    private ConcreteElementMetadata(String path, String classifierPath, SourceInformation sourceInfo, ImmutableList<String> externalReferences)
    {
        super(path, classifierPath);
        this.sourceInfo = Objects.requireNonNull(sourceInfo, "source information is required");
        if (!sourceInfo.isValid())
        {
            throw new IllegalArgumentException("Invalid source information for " + this.path);
        }
        this.externalReferences = externalReferences;
    }

    public SourceInformation getSourceInformation()
    {
        return this.sourceInfo;
    }

    public ImmutableList<String> getExternalReferences()
    {
        return this.externalReferences;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ConcreteElementMetadata))
        {
            return false;
        }

        ConcreteElementMetadata that = (ConcreteElementMetadata) other;
        return this.path.equals(that.path) &&
                this.classifierPath.equals(that.classifierPath) &&
                this.sourceInfo.equals(that.sourceInfo) &&
                this.externalReferences.equals(that.externalReferences);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.path.hashCode();
        hashCode = 31 * hashCode + this.classifierPath.hashCode();
        hashCode = 31 * hashCode + this.sourceInfo.hashCode();
        hashCode = 31 * hashCode + this.externalReferences.hashCode();
        return hashCode;
    }

    @Override
    protected void appendStringInfo(StringBuilder builder)
    {
        this.sourceInfo.appendMessage(builder.append(" sourceInfo="));
        if (this.externalReferences.notEmpty())
        {
            builder.append(" externalReferences=[");
            this.externalReferences.forEach(ref -> StringEscape.escape(builder.append("'"), ref).append("', "));
            builder.setLength(builder.length() - 2);
            builder.append(']');
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(int initExtRefCapacity)
    {
        return new Builder(initExtRefCapacity);
    }

    public static class Builder
    {
        private String path;
        private String classifierPath;
        private SourceInformation sourceInfo;
        private final MutableList<String> externalReferences;

        private Builder()
        {
            this.externalReferences = Lists.mutable.empty();
        }

        private Builder(int initExtRefCapacity)
        {
            this.externalReferences = Lists.mutable.ofInitialCapacity(initExtRefCapacity);
        }

        public Builder withPath(String path)
        {
            this.path = path;
            return this;
        }

        public Builder withClassifierPath(String classifierPath)
        {
            this.classifierPath = classifierPath;
            return this;
        }

        public Builder withSourceInformation(SourceInformation sourceInfo)
        {
            this.sourceInfo = sourceInfo;
            return this;
        }

        public Builder withExternalReference(String referenceId)
        {
            this.externalReferences.add(Objects.requireNonNull(referenceId));
            return this;
        }

        public Builder withExternalReferences(Iterable<? extends String> referenceIds)
        {
            referenceIds.forEach(this::withExternalReference);
            return this;
        }

        public ConcreteElementMetadata build()
        {
            return new ConcreteElementMetadata(this.path, this.classifierPath, this.sourceInfo, processExternalReferences());
        }

        private ImmutableList<String> processExternalReferences()
        {
            this.externalReferences.sortThis();
            String[] prev = new String[1];
            this.externalReferences.removeIf(current ->
            {
                if (current.equals(prev[0]))
                {
                    return true;
                }
                prev[0] = current;
                return false;
            });
            return this.externalReferences.toImmutable();
        }
    }
}
