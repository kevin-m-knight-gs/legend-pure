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

import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Objects;

public abstract class DeserializedElement
{
    public abstract String getName();

    public abstract String getClassifierReferenceId();

    public abstract SourceInformation getSourceInformation();

    public abstract ListIterable<? extends PropertyValues> getPropertyValues();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DeserializedElement))
        {
            return false;
        }

        DeserializedElement that = (DeserializedElement) other;
        return Objects.equals(this.getName(), that.getName()) &&
                Objects.equals(this.getClassifierReferenceId(), that.getClassifierReferenceId()) &&
                Objects.equals(this.getSourceInformation(), that.getSourceInformation()) &&
                this.getPropertyValues().equals(that.getPropertyValues());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getName(), getClassifierReferenceId(), getSourceInformation());
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(DeserializedElement.class.getSimpleName()).append('{');
        if (getClassifierReferenceId() != null)
        {
            builder.append("classifier=").append(getClassifierReferenceId()).append(' ');
        }
        if (getName() != null)
        {
            builder.append("name='").append(getName()).append("' ");
        }
        if (getSourceInformation() != null)
        {
            getSourceInformation().appendMessage(builder.append("sourceInfo=")).append(' ');
        }
        builder.append("propertyValues=[");
        ListIterable<? extends PropertyValues> propertyValues = getPropertyValues();
        if (propertyValues.notEmpty())
        {
            propertyValues.forEach(pv -> builder.append(pv.getPropertyName()).append("=").append(pv.getValues()).append(", "));
            builder.setLength(builder.length() - 2);
        }
        return builder.append("]}").toString();
    }
}
