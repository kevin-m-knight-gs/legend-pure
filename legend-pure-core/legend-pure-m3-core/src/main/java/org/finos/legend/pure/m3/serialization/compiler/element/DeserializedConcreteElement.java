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

import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.impl.list.primitive.IntInterval;

import java.util.Objects;

public abstract class DeserializedConcreteElement extends DeserializedElement
{
    public abstract String getPath();

    public abstract int getReferenceIdVersion();

    public abstract IntIterable getInternalElementIds();

    public abstract DeserializedElement getInternalElement(int id);

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DeserializedConcreteElement))
        {
            return false;
        }
        DeserializedConcreteElement that = (DeserializedConcreteElement) other;
        if ((this.getReferenceIdVersion() != that.getReferenceIdVersion()) ||
                !this.getPath().equals(that.getPath()) ||
                !Objects.equals(this.getName(), that.getName()) ||
                !Objects.equals(this.getClassifierReferenceId(), that.getClassifierReferenceId()) ||
                !Objects.equals(this.getSourceInformation(), that.getSourceInformation()) ||
                !this.getPropertyValues().equals(that.getPropertyValues()))
        {
            return false;
        }

        IntIterable thisIds = this.getInternalElementIds();
        IntIterable thatIds = that.getInternalElementIds();
        if (thisIds.size() != thatIds.size())
        {
            return false;
        }
        IntIterable thatIdsCol = ((thatIds instanceof IntInterval) || (thatIds instanceof IntSet)) ? thatIds : thatIds.toSet();
        return thisIds.allSatisfy(id -> thatIdsCol.contains(id) && this.getInternalElement(id).equals(that.getInternalElement(id)));
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getPath(), getReferenceIdVersion(), getClassifierReferenceId(), getSourceInformation());
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(DeserializedElement.class.getSimpleName());
        builder.append("{path=").append(getPath());
        if (getClassifierReferenceId() != null)
        {
            builder.append(" classifier=").append(getClassifierReferenceId());
        }
        if (getName() != null)
        {
            builder.append(" name='").append(getName()).append("'");
        }
        if (getSourceInformation() != null)
        {
            getSourceInformation().appendMessage(builder.append(" sourceInfo="));
        }
        builder.append(" propertyValues=[");
        getPropertyValues().appendString(builder, " propertyValues=[", ", ", "]");
        builder.append(" referenceIdVersion=").append(getReferenceIdVersion());
        builder.append(" internalElements=[");
        IntIterable ids = getInternalElementIds();
        if (ids.notEmpty())
        {
            ids.forEach(id -> builder.append(id).append('=').append(getInternalElement(id)).append(", "));
            builder.setLength(builder.length() - 2);
        }
        return builder.append("]}").toString();
    }
}
