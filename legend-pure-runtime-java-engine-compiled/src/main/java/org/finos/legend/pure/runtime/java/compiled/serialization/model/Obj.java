// Copyright 2020 Goldman Sachs
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Objects;

public class Obj
{
    private final SourceInformation sourceInformation;
    private final String identifier;
    private final String classifier;
    private final String name;
    private final ListIterable<PropertyValue> properties;

    public Obj(SourceInformation sourceInformation, String identifier, String classifier, String name, ListIterable<PropertyValue> propertiesList)
    {
        this.sourceInformation = sourceInformation;
        this.identifier = identifier;
        this.classifier = classifier;
        this.name = name;
        this.properties = (propertiesList == null) ? Lists.immutable.empty() : propertiesList;
    }

    public SourceInformation getSourceInformation()
    {
        return this.sourceInformation;
    }

    public String getIdentifier()
    {
        return this.identifier;
    }

    public String getClassifier()
    {
        return this.classifier;
    }

    public String getName()
    {
        return this.name;
    }

    public ListIterable<PropertyValue> getPropertyValues()
    {
        return this.properties;
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

        Obj that = (Obj) other;
        return this.identifier.equals(that.identifier) &&
                this.classifier.equals(that.classifier) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.sourceInformation, that.sourceInformation) &&
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
        builder.append('{');
        if (this.sourceInformation != null)
        {
            builder.append("sourceInformation=");
            this.sourceInformation.writeMessage(builder);
            builder.append(", ");
        }
        builder.append("classifier='");
        builder.append(this.classifier);
        builder.append("', identifier='");
        builder.append(this.identifier);
        builder.append("', name='");
        builder.append(this.name);
        builder.append("', properties=");
        this.properties.appendString(builder);
        builder.append('}');
        return builder.toString();
    }

    public Obj applyUpdates(Iterable<? extends ObjUpdate> updates)
    {
        // Consolidate and validate updates
        MutableMap<String, MutableList<RValue>> consolidatedUpdates = Maps.mutable.empty();
        PropertyValueConsumer collector = new PropertyValueConsumer()
        {
            @Override
            protected void accept(PropertyValueMany many)
            {
                ListIterable<RValue> values = many.getValues();
                if (values.notEmpty())
                {
                    consolidatedUpdates.getIfAbsentPut(many.getProperty(), Lists.mutable::empty).addAllIterable(values);
                }
            }

            @Override
            protected void accept(PropertyValueOne one)
            {
                RValue value = one.getValue();
                if (value != null)
                {
                    consolidatedUpdates.getIfAbsentPut(one.getProperty(), Lists.mutable::empty).add(value);
                }
            }
        };
        updates.forEach(update ->
        {
            if (!this.identifier.equals(update.getIdentifier()) || !this.classifier.equals(update.getClassifier()))
            {
                throw new IllegalArgumentException("Cannot apply update for " + update.getClassifier() + ":" + update.getIdentifier() + " to " + this.classifier + ":" + this.identifier);
            }
            update.getPropertyValues().forEach(collector);
        });

        // If there are no updates, return this
        if (consolidatedUpdates.isEmpty())
        {
            return this;
        }

        // If there are updates, compute new property values
        MutableList<PropertyValue> updatedPropertyValues = this.properties.collect(propertyValue ->
        {
            String property = propertyValue.getProperty();
            MutableList<RValue> additions = consolidatedUpdates.remove(property); // remove so we know what we have left over at the end
            if ((additions == null) || additions.isEmpty())
            {
                return propertyValue;
            }
            ListIterable<RValue> newValues = propertyValue.visit(new PropertyValueVisitor<ListIterable<RValue>>()
            {
                @Override
                public ListIterable<RValue> visit(PropertyValueMany many)
                {
                    ListIterable<RValue> values = many.getValues();
                    if (values.isEmpty())
                    {
                        return additions;
                    }
                    return Lists.mutable.<RValue>ofInitialCapacity(values.size() + additions.size())
                            .withAll(values)
                            .withAll(additions);
                }

                @Override
                public ListIterable<RValue> visit(PropertyValueOne one)
                {
                    RValue value = one.getValue();
                    if (value != null)
                    {
                        additions.add(0, one.getValue());
                    }
                    return additions;
                }
            });
            return newPropertyValue(property, newValues);
        }, Lists.mutable.ofInitialCapacity(this.properties.size()));
        consolidatedUpdates.forEach((property, values) -> updatedPropertyValues.add(newPropertyValue(property, values)));

        // Return a copy of this with updated property values
        return cloneWithNewPropertyValues(updatedPropertyValues);
    }

    protected Obj cloneWithNewPropertyValues(ListIterable<PropertyValue> newPropertyValues)
    {
        return new Obj(this.sourceInformation, this.identifier, this.classifier, this.name, newPropertyValues);
    }

    private static PropertyValue newPropertyValue(String property, ListIterable<RValue> values)
    {
        return (values.size() == 1) ? new PropertyValueOne(property, values.get(0)) : new PropertyValueMany(property, values);
    }
}
