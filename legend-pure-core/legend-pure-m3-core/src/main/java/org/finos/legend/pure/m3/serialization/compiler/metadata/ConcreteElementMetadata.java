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
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.util.Objects;

public class ConcreteElementMetadata extends PackageableElementMetadata
{
    private final SourceInformation sourceInfo;
    private final int referenceIdVersion;
    private final ImmutableList<ExternalReference> externalReferences;

    private ConcreteElementMetadata(String path, String classifierPath, SourceInformation sourceInfo, int referenceIdVersion, ImmutableList<ExternalReference> externalReferences)
    {
        super(path, classifierPath);
        this.sourceInfo = Objects.requireNonNull(sourceInfo, "source information is required");
        if (!sourceInfo.isValid())
        {
            throw new IllegalArgumentException("Invalid source information for " + this.path);
        }
        this.referenceIdVersion = referenceIdVersion;
        this.externalReferences = externalReferences;
    }

    public SourceInformation getSourceInformation()
    {
        return this.sourceInfo;
    }

    public int getReferenceIdVersion()
    {
        return this.referenceIdVersion;
    }

    public ImmutableList<ExternalReference> getExternalReferences()
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
                (this.referenceIdVersion == that.referenceIdVersion) &&
                this.externalReferences.equals(that.externalReferences);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.path.hashCode();
        hashCode = 31 * hashCode + this.classifierPath.hashCode();
        hashCode = 31 * hashCode + this.sourceInfo.hashCode();
        hashCode = 31 * hashCode + this.referenceIdVersion;
        hashCode = 31 * hashCode + this.externalReferences.hashCode();
        return hashCode;
    }

    @Override
    protected void appendStringInfo(StringBuilder builder)
    {
        this.sourceInfo.appendMessage(builder.append(" sourceInfo="));
        builder.append(" referenceIdVersion=").append(this.referenceIdVersion);
        if (this.externalReferences.notEmpty())
        {
            builder.append(" externalReferences={");
            this.externalReferences.forEach(extRef -> StringEscape.escape(builder.append('\''), extRef.getReferenceId()).append("', "));
            builder.setLength(builder.length() - 2);
            builder.append('}');
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
        private Integer referenceIdVersion;
        private final MutableList<ExternalReference> externalReferences;

        private Builder()
        {
            this.externalReferences = Lists.mutable.empty();
        }

        private Builder(int initExtRefCapacity)
        {
            this.externalReferences = Lists.mutable.ofInitialCapacity(initExtRefCapacity);
        }

        public String getPath()
        {
            return this.path;
        }

        public Builder withPath(String path)
        {
            this.path = path;
            return this;
        }

        public String getClassifierPath()
        {
            return this.classifierPath;
        }

        public Builder withClassifierPath(String classifierPath)
        {
            this.classifierPath = classifierPath;
            return this;
        }

        public SourceInformation getSourceInformation()
        {
            return this.sourceInfo;
        }

        public Builder withSourceInformation(SourceInformation sourceInfo)
        {
            this.sourceInfo = sourceInfo;
            return this;
        }

        public Builder withReferenceIdVersion(int referenceIdVersion)
        {
            this.referenceIdVersion = referenceIdVersion;
            return this;
        }

        public Builder withExternalReference(String referenceId)
        {
            return withExternalReference(ExternalReference.builder().withReferenceId(referenceId).build());
        }

        public Builder withExternalReference(String referenceId, Iterable<? extends BackReference> backReferences)
        {
            return withExternalReference(ExternalReference.builder()
                    .withReferenceId(referenceId)
                    .withBackReferences(backReferences)
                    .build());
        }

        public Builder withExternalReference(String referenceId, BackReference... backReferences)
        {
            return withExternalReference(ExternalReference.builder()
                    .withReferenceId(referenceId)
                    .withBackReferences(backReferences)
                    .build());
        }

        public Builder withExternalReference(ExternalReference externalReference)
        {
            this.externalReferences.add(Objects.requireNonNull(externalReference));
            return this;
        }

        public Builder withExternalReferences(Iterable<? extends ExternalReference> externalReferences)
        {
            externalReferences.forEach(this::withExternalReference);
            return this;
        }

        public Builder withExternalReferences(ExternalReference... externalReferences)
        {
            ArrayIterate.forEach(externalReferences, this::withExternalReference);
            return this;
        }

        public ConcreteElementMetadata build()
        {
            Objects.requireNonNull(this.path, "path is required");
            Objects.requireNonNull(this.referenceIdVersion, "reference id version is required");
            return new ConcreteElementMetadata(this.path, this.classifierPath, this.sourceInfo, this.referenceIdVersion, processExternalReferences());
        }

        private ImmutableList<ExternalReference> processExternalReferences()
        {
            if (this.externalReferences.size() > 1)
            {
                this.externalReferences.sortThisBy(ExternalReference::getReferenceId);
                int index = 0;
                while (index < this.externalReferences.size())
                {
                    int start = index++;
                    ExternalReference current = this.externalReferences.get(start);
                    String currentId = current.getReferenceId();
                    while ((index < this.externalReferences.size()) && currentId.equals(this.externalReferences.get(index).getReferenceId()))
                    {
                        index++;
                    }
                    if (index > start + 1)
                    {
                        // Multiple ExternalReference objects for the same reference: merge them
                        ExternalReference merged = current.merge(this.externalReferences.subList(start + 1, index));
                        this.externalReferences.set(start++, merged);
                        while (start < index)
                        {
                            // set to null to mark for removal
                            this.externalReferences.set(start++, null);
                        }
                    }
                }
                this.externalReferences.removeIf(Objects::isNull);
            }
            return this.externalReferences.toImmutable();
        }
    }
}
