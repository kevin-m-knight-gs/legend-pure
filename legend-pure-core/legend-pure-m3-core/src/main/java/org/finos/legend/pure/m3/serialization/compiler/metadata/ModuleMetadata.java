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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ModuleMetadata
{
    private final String name;
    private final ImmutableList<ConcreteElementMetadata> elements;

    private ModuleMetadata(String name, ImmutableList<ConcreteElementMetadata> elements)
    {
        this.name = name;
        this.elements = elements;
    }

    public ModuleMetadata(String name, Iterable<? extends ConcreteElementMetadata> elements)
    {
        this(validateName(name), processElements(name, elements));
    }

    public ModuleMetadata(String name, ConcreteElementMetadata... elements)
    {
        this(name, Arrays.asList(elements));
    }

    public String getName()
    {
        return this.name;
    }

    public int getElementCount()
    {
        return this.elements.size();
    }

    public ImmutableList<ConcreteElementMetadata> getElements()
    {
        return this.elements;
    }

    public void forEachElement(Consumer<? super ConcreteElementMetadata> consumer)
    {
        this.elements.forEach(consumer);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ModuleMetadata))
        {
            return false;
        }

        ModuleMetadata that = (ModuleMetadata) other;
        return this.name.equals(that.name) && this.elements.equals(that.elements);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("<").append(getClass().getSimpleName())
                .append(" name='").append(this.name).append("'");
        this.elements.asLazy().collect(PackageableElementMetadata::getPath).appendString(builder, " elements=[", ", ", "]>");
        return builder.toString();
    }

    public ModuleMetadata withElement(ConcreteElementMetadata newElement)
    {
        Objects.requireNonNull(newElement, "element may not be null");
        return update(Maps.mutable.with(newElement.getPath(), newElement), (Predicate<ConcreteElementMetadata>) null);
    }

    public ModuleMetadata withElements(ConcreteElementMetadata... newElements)
    {
        return withElements(Arrays.asList(newElements));
    }

    public ModuleMetadata withElements(Iterable<? extends ConcreteElementMetadata> newElements)
    {
        return update(newElements, (Predicate<ConcreteElementMetadata>) null);
    }

    public ModuleMetadata withoutElements(String... toRemove)
    {
        return update(null, Sets.immutable.with(toRemove));
    }

    public ModuleMetadata withoutElements(Iterable<? extends String> toRemove)
    {
        return update(null, toRemove);
    }

    public ModuleMetadata withoutElements(Predicate<? super ConcreteElementMetadata> predicate)
    {
        return update(null, predicate);
    }

    public ModuleMetadata update(Iterable<? extends ConcreteElementMetadata> newElements, Iterable<? extends String> toRemove)
    {
        return update(newElements, getToRemovePredicate(toRemove));
    }

    public ModuleMetadata update(Iterable<? extends ConcreteElementMetadata> newElements, Predicate<? super ConcreteElementMetadata> toRemove)
    {
        MutableMap<String, ConcreteElementMetadata> newElementsByPath;
        if (newElements == null)
        {
            newElementsByPath = null;
        }
        else
        {
            newElementsByPath = (newElements instanceof Collection) ? Maps.mutable.ofInitialCapacity(((Collection<?>) newElements).size()) : Maps.mutable.empty();
            newElements.forEach(e ->
            {
                ConcreteElementMetadata old = newElementsByPath.put(Objects.requireNonNull(e, "element may not be null").getPath(), e);
                if ((old != null) && !old.equals(e))
                {
                    throw new IllegalArgumentException("Conflict for element: " + e.getPath());
                }
            });
        }
        return update(newElementsByPath, toRemove);
    }

    private ModuleMetadata update(MutableMap<String, ConcreteElementMetadata> newElementsByPath, Predicate<? super ConcreteElementMetadata> toRemove)
    {
        if ((newElementsByPath == null) || newElementsByPath.isEmpty())
        {
            // No new elements, so no validation or sorting is necessary
            if (toRemove != null)
            {
                MutableList<ConcreteElementMetadata> newElementList = this.elements.reject(toRemove::test, Lists.mutable.empty());
                if (newElementList.size() != this.elements.size())
                {
                    return new ModuleMetadata(this.name, newElementList.toImmutable());
                }
            }
            return this;
        }

        MutableList<ConcreteElementMetadata> newElementList = Lists.mutable.empty();
        this.elements.forEach(element ->
        {
            if ((toRemove == null) || !toRemove.test(element))
            {
                ConcreteElementMetadata replacement = newElementsByPath.remove(element.getPath());
                newElementList.add((replacement == null) ? element : replacement);
            }
        });
        if (newElementsByPath.notEmpty())
        {
            newElementList.addAll(newElementsByPath.values());
            newElementList.sortThisBy(PackageableElementMetadata::getPath);
        }
        validateSourceInfo(this.name, newElementList);
        return new ModuleMetadata(this.name, newElementList.toImmutable());
    }

    private static Predicate<ConcreteElementMetadata> getToRemovePredicate(Iterable<? extends String> toRemove)
    {
        if (toRemove == null)
        {
            return null;
        }
        SetIterable<? extends String> set = (toRemove instanceof SetIterable) ?
                                            (SetIterable<? extends String>) toRemove :
                                            ((toRemove instanceof Set) ?
                                             SetAdapter.adapt((Set<? extends String>) toRemove) :
                                             Sets.mutable.withAll(toRemove));
        switch (set.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                String elementToRemove = set.getAny();
                return (elementToRemove == null) ? null : emd -> elementToRemove.equals(emd.getPath());
            }
            default:
            {
                return emd -> set.contains(emd.getPath());
            }
        }
    }

    private static String validateName(String name)
    {
        Objects.requireNonNull(name, "name may not be null");
        if (name.isEmpty())
        {
            throw new IllegalArgumentException("name may not be empty");
        }
        return name;
    }

    private static ImmutableList<ConcreteElementMetadata> processElements(String moduleName, Iterable<? extends ConcreteElementMetadata> elements)
    {
        MutableList<ConcreteElementMetadata> list = (elements instanceof Collection) ? Lists.mutable.ofInitialCapacity(((Collection<?>) elements).size()) : Lists.mutable.empty();
        elements.forEach(emd -> list.add(Objects.requireNonNull(emd, "element metadata may not be null")));
        if (list.size() > 1)
        {
            list.sortThisBy(PackageableElementMetadata::getPath);
            ConcreteElementMetadata[] prev = new ConcreteElementMetadata[1];
            list.removeIf(current ->
            {
                ConcreteElementMetadata previous = prev[0];
                if ((previous == null) || !previous.getPath().equals(current.getPath()))
                {
                    prev[0] = current;
                    return false;
                }
                if (!previous.equals(current))
                {
                    throw new IllegalArgumentException("Conflict for element: " + current.getPath());
                }
                return true;
            });
        }
        validateSourceInfo(moduleName, list);
        return list.toImmutable();
    }

    private static void validateSourceInfo(String moduleName, ListIterable<ConcreteElementMetadata> elements)
    {
        if (elements.isEmpty())
        {
            return;
        }

        if (elements.size() == 1)
        {
            SourceInformation sourceInfo = elements.get(0).getSourceInformation();
            if (!isSourceInModule(moduleName, sourceInfo.getSourceId()))
            {
                throw new IllegalArgumentException("Invalid source '" + sourceInfo.getSourceId() + "' in module '" + moduleName + "' with element " + elements.get(0).getPath());
            }
            return;
        }

        MutableMap<String, MutableList<ConcreteElementMetadata>> elementsBySource = Maps.mutable.empty();
        elements.forEach(e -> elementsBySource.getIfAbsentPut(e.getSourceInformation().getSourceId(), Lists.mutable::empty).add(e));
        elementsBySource.forEachKeyValue((sourceId, list) ->
        {
            if (!isSourceInModule(moduleName, sourceId))
            {
                StringBuilder builder = new StringBuilder("Invalid source '").append(sourceId).append("' in module '").append(moduleName).append("' with elements ");
                list.collect(PackageableElementMetadata::getPath).sortThis().appendString(builder, ", ");
                throw new IllegalArgumentException(builder.toString());
            }
            if (list.size() > 1)
            {
                // We sort by the start position, then check if there is any overlap between subsequent source info
                list.sortThis((e1, e2) -> SourceInformation.compareByStartPosition(e1.getSourceInformation(), e2.getSourceInformation()))
                        .injectInto((ConcreteElementMetadata) null, (previous, current) ->
                        {
                            if (previous != null)
                            {
                                SourceInformation previousSI = previous.getSourceInformation();
                                SourceInformation currentSI = current.getSourceInformation();
                                if (previousSI.intersects(currentSI))
                                {
                                    StringBuilder builder = new StringBuilder("Overlapping source information for ").append(previous.getPath());
                                    previousSI.appendMessage(builder.append(" (")).append(") and ").append(current.getPath());
                                    currentSI.appendMessage(builder.append(" (")).append(")");
                                    throw new IllegalArgumentException(builder.toString());
                                }
                            }
                            return current;
                        });
            }
        });
    }

    static boolean isSourceInModule(String moduleName, String sourceId)
    {
        return (sourceId != null) &&
                (sourceId.length() >= (moduleName.length() + 2)) &&
                (sourceId.charAt(0) == '/') &&
                (sourceId.charAt(moduleName.length() + 1) == '/') &&
                sourceId.startsWith(moduleName, 1);
    }
}
