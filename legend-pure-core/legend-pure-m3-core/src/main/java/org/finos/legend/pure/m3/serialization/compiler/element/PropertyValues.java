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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;

import java.util.Objects;

public abstract class PropertyValues
{
    public abstract String getPropertyName();

    public abstract ListIterable<String> getRealKey();

    public abstract ListIterable<ValueOrReference> getValues();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof PropertyValues))
        {
            return false;
        }

        PropertyValues that = (PropertyValues) other;
        return this.getPropertyName().equals(that.getPropertyName()) &&
                this.getRealKey().equals(that.getRealKey()) &&
                this.getValues().equals(that.getValues());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getPropertyName(), getValues());
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder(PropertyValues.class.getSimpleName())
                .append("{property=").append(getPropertyName());
        getRealKey().appendString(builder, " realKey=[", ", ", "]");
        getValues().appendString(builder, " values=[", ", ", "]}");
        return builder.toString();
    }

    public static PropertyValues newPropertyValues(String propertyName, ListIterable<String> realKey, ValueOrReference... values)
    {
        return newPropertyValues(propertyName, realKey, Lists.immutable.with(values));
    }

    public static PropertyValues newPropertyValues(String propertyName, ListIterable<String> realKey, ListIterable<ValueOrReference> values)
    {
        return new PropertyValues()
        {
            @Override
            public String getPropertyName()
            {
                return propertyName;
            }

            @Override
            public ListIterable<String> getRealKey()
            {
                return realKey;
            }

            @Override
            public ListIterable<ValueOrReference> getValues()
            {
                return values;
            }
        };
    }
}
