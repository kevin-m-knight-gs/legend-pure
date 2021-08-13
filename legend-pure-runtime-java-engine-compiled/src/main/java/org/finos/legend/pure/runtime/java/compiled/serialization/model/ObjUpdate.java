// Copyright 2021 Goldman Sachs
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

package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import org.eclipse.collections.api.list.ListIterable;

public class ObjUpdate implements ObjOrUpdate
{
    private final String identifier;
    private final String classifier;
    private final ListIterable<PropertyValue> properties;

    public ObjUpdate(String identifier, String classifier, ListIterable<PropertyValue> additionalPropertyValues)
    {
        this.identifier = identifier;
        this.classifier = classifier;
        this.properties = additionalPropertyValues;
    }

    @Override
    public String getIdentifier()
    {
        return this.identifier;
    }

    @Override
    public String getClassifier()
    {
        return this.classifier;
    }

    @Override
    public ListIterable<PropertyValue> getPropertyValues()
    {
        return this.properties;
    }

    @Override
    public <T> T visit(ObjOrUpdateVisitor<T> visitor)
    {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (other == null || this.getClass() != other.getClass())
        {
            return false;
        }

        ObjUpdate that = (ObjUpdate) other;
        return this.identifier.equals(that.identifier) &&
                this.classifier.equals(that.classifier) &&
                this.properties.equals(that.properties);
    }

    @Override
    public int hashCode()
    {
        return this.classifier.hashCode() + (31 * this.identifier.hashCode());
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName());
        builder.append("{classifier='").append(this.classifier).append("'");
        builder.append(", identifier='").append(this.identifier).append("'");
        this.properties.appendString(builder, ", properties=[", ", ", "]");
        builder.append('}');
        return builder.toString();
    }
}
