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

import org.eclipse.collections.api.list.ImmutableList;

import java.util.Objects;

public class DeserializedConcreteElement
{
    private final String path;
    private final int referenceIdVersion;
    private final ImmutableList<DeserializedElement> elements;

    private DeserializedConcreteElement(String path, int referenceIdVersion, ImmutableList<DeserializedElement> elements)
    {
        this.path = Objects.requireNonNull(path);
        this.referenceIdVersion = referenceIdVersion;
        this.elements = Objects.requireNonNull(elements);
        if (this.elements.isEmpty())
        {
            throw new IllegalArgumentException("elements may not be empty");
        }
    }

    /**
     * Get the package path of the concrete element that was deserialized.
     *
     * @return concrete element package path
     */
    public String getPath()
    {
        return this.path;
    }

    /**
     * Get the reference id version used when the element was serialized.
     *
     * @return reference id version
     */
    public int getReferenceIdVersion()
    {
        return this.referenceIdVersion;
    }

    /**
     * Get the elements that were deserialized, including the concrete element itself and all component instances. The
     * concrete element itself will always be first in the list.
     *
     * @return deserialized elements
     */
    public ImmutableList<DeserializedElement> getElements()
    {
        return this.elements;
    }

    /**
     * Get the element at the given index. This index is also its id as an internal element.
     *
     * @param index element index
     * @return element at index
     */
    public DeserializedElement getElement(int index)
    {
        return this.elements.get(index);
    }

    /**
     * Get the deserialized concrete element itself. This is equivalent to {@link #getElement}(0).
     *
     * @return deserialized concrete element
     */
    public DeserializedElement getConcreteElement()
    {
        return getElement(0);
    }

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
        return (this.referenceIdVersion == that.referenceIdVersion) &&
                this.path.equals(that.path) &&
                this.elements.equals(that.elements);
    }

    @Override
    public int hashCode()
    {
        return (31 * getPath().hashCode()) + Integer.hashCode(getReferenceIdVersion());
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(DeserializedElement.class.getSimpleName())
                .append("{path=").append(this.path)
                .append(" referenceIdVersion=").append(this.referenceIdVersion);
        this.elements.appendString(builder, " elements=[", ", ", "]}");
        return builder.toString();
    }

    public static DeserializedConcreteElement newDeserializedConcreteElement(String path, int referenceIdVersion, ImmutableList<DeserializedElement> elements)
    {
        return new DeserializedConcreteElement(path, referenceIdVersion, elements);
    }
}
