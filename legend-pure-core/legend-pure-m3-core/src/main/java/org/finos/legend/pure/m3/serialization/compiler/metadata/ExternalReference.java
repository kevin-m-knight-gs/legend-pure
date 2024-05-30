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
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Arrays;
import java.util.Objects;

public class ExternalReference
{
    private final String referenceId;
    private final ImmutableList<BackReference> backReferences;

    private ExternalReference(String referenceId, ImmutableList<BackReference> backReferences)
    {
        this.referenceId = validateReferenceId(referenceId);
        this.backReferences = backReferences;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ExternalReference))
        {
            return false;
        }

        ExternalReference that = (ExternalReference) other;
        return this.referenceId.equals(that.referenceId) && this.backReferences.equals(that.backReferences);
    }

    @Override
    public int hashCode()
    {
        return this.referenceId.hashCode() + 31 * this.backReferences.hashCode();
    }

    @Override
    public String toString()
    {
        return appendString(new StringBuilder()).toString();
    }

    StringBuilder appendString(StringBuilder builder)
    {
        builder.append(getClass().getSimpleName());
        builder.append("{referenceId=").append(this.referenceId);
        if (this.backReferences.notEmpty())
        {
            builder.append(", backRefs=[");
            this.backReferences.forEach(br -> br.appendString(builder).append(", "));
            builder.setLength(builder.length() - 2);
            builder.append(']');
        }
        return builder.append('}');
    }

    public String getReferenceId()
    {
        return this.referenceId;
    }

    public ImmutableList<BackReference> getBackReferences()
    {
        return this.backReferences;
    }

    public ExternalReference merge(Iterable<? extends ExternalReference> references)
    {
        Builder builder = new Builder(this);
        references.forEach(builder::addExternalReference);
        return builder.build();
    }

    public ExternalReference merge(ExternalReference... references)
    {
        return ((references == null) || (references.length == 0)) ? this : merge(Arrays.asList(references));
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(int backRefCapacity)
    {
        return new Builder(backRefCapacity);
    }

    public static class Builder
    {
        private String referenceId;
        private final MutableList<BackReference> backReferences;

        private Builder()
        {
            this.backReferences = Lists.mutable.empty();
        }

        private Builder(int backRefCapacity)
        {
            this.backReferences = Lists.mutable.ofInitialCapacity(backRefCapacity);
        }

        private Builder(ExternalReference extRef)
        {
            this.referenceId = extRef.referenceId;
            this.backReferences = Lists.mutable.withAll(extRef.backReferences);
        }

        public void setReferenceId(String referenceId)
        {
            this.referenceId = referenceId;
        }

        public Builder withReferenceId(String referenceId)
        {
            setReferenceId(referenceId);
            return this;
        }

        public void addBackReference(BackReference backReference)
        {
            this.backReferences.add(Objects.requireNonNull(backReference));
        }

        public Builder withBackReference(BackReference backReference)
        {
            addBackReference(backReference);
            return this;
        }

        public Builder withBackReferences(Iterable<? extends BackReference> backReferences)
        {
            backReferences.forEach(this::addBackReference);
            return this;
        }

        public Builder withBackReferences(BackReference... backReferences)
        {
            return withBackReferences(Arrays.asList(backReferences));
        }

        public Builder withApplication(String functionExpression)
        {
            return withBackReference(BackReference.newApplication(functionExpression));
        }

        public Builder withModelElement(String element)
        {
            return withBackReference(BackReference.newModelElement(element));
        }

        public Builder withPropertyFromAssociation(String property)
        {
            return withBackReference(BackReference.newPropertyFromAssociation(property));
        }

        public Builder withQualifiedPropertyFromAssociation(String qualifiedProperty)
        {
            return withBackReference(BackReference.newQualifiedPropertyFromAssociation(qualifiedProperty));
        }

        public Builder withReferenceUsage(String owner, String property, int offset)
        {
            return withBackReference(BackReference.newReferenceUsage(owner, property, offset));
        }

        public Builder withReferenceUsage(String owner, String property, int offset, SourceInformation sourceInfo)
        {
            return withBackReference(BackReference.newReferenceUsage(owner, property, offset, sourceInfo));
        }

        public Builder withSpecialization(String specialization)
        {
            return withBackReference(BackReference.newSpecialization(specialization));
        }

        public void addExternalReference(ExternalReference externalReference)
        {
            Objects.requireNonNull(externalReference);
            if (this.referenceId == null)
            {
                this.referenceId = externalReference.referenceId;
            }
            else if (!this.referenceId.equals(externalReference.referenceId))
            {
                throw new IllegalArgumentException("Cannot merge external reference for '" + externalReference.referenceId + "' into '" + this.referenceId + "'");
            }
            this.backReferences.addAll(externalReference.backReferences.castToList());
        }

        public Builder withExternalReference(ExternalReference externalReference)
        {
            addExternalReference(externalReference);
            return this;
        }

        public ExternalReference build()
        {
            if (this.backReferences.size() > 1)
            {
                // sort and remove duplicates
                this.backReferences.sortThis(ExternalReference::compareBackReferences);
                BackReference[] previous = new BackReference[1];
                this.backReferences.removeIf(current ->
                {
                    if (current.equals(previous[0]))
                    {
                        return true;
                    }
                    previous[0] = current;
                    return false;
                });
            }
            return new ExternalReference(this.referenceId, this.backReferences.toImmutable());
        }
    }

    private static String validateReferenceId(String referenceId)
    {
        Objects.requireNonNull(referenceId, "reference id is required");
        if (referenceId.isEmpty())
        {
            throw new IllegalArgumentException("reference id may not be empty");
        }
        return referenceId;
    }

    private static int compareBackReferences(BackReference ref1, BackReference ref2)
    {
        if (ref1.getClass() != ref2.getClass())
        {
            return ref1.getClass().getSimpleName().compareTo(ref2.getClass().getSimpleName());
        }
        return ref1.visit(new BackReferenceVisitor<Integer>()
        {
            @Override
            public Integer visit(BackReference.Application application)
            {
                return application.getFunctionExpression().compareTo(((BackReference.Application) ref2).getFunctionExpression());
            }

            @Override
            public Integer visit(BackReference.ModelElement modelElement)
            {
                return modelElement.getElement().compareTo(((BackReference.ModelElement) ref2).getElement());
            }

            @Override
            public Integer visit(BackReference.PropertyFromAssociation propertyFromAssociation)
            {
                return propertyFromAssociation.getProperty().compareTo(((BackReference.PropertyFromAssociation) ref2).getProperty());
            }

            @Override
            public Integer visit(BackReference.QualifiedPropertyFromAssociation qualifiedPropertyFromAssociation)
            {
                return qualifiedPropertyFromAssociation.getQualifiedProperty().compareTo(((BackReference.QualifiedPropertyFromAssociation) ref2).getQualifiedProperty());
            }

            @Override
            public Integer visit(BackReference.ReferenceUsage referenceUsage)
            {
                BackReference.ReferenceUsage other = (BackReference.ReferenceUsage) ref2;
                int cmp = referenceUsage.getOwner().compareTo(other.getOwner());
                if (cmp == 0)
                {
                    cmp = referenceUsage.getProperty().compareTo(other.getProperty());
                    if (cmp == 0)
                    {
                        cmp = Integer.compare(referenceUsage.getOffset(), other.getOffset());
                    }
                }
                return cmp;
            }

            @Override
            public Integer visit(BackReference.Specialization specialization)
            {
                return specialization.getGeneralization().compareTo(((BackReference.Specialization) ref2).getGeneralization());
            }
        });
    }
}
