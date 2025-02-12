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
import org.eclipse.collections.api.set.MutableSet;
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
        return builder(this).withElement(newElement, true).build();
    }

    public ModuleMetadata withElements(ConcreteElementMetadata... newElements)
    {
        return withElements(Arrays.asList(newElements));
    }

    public ModuleMetadata withElements(Iterable<? extends ConcreteElementMetadata> newElements)
    {
        return builder(this).withElements(newElements, true).build();
    }

    public ModuleMetadata withoutElement(String toRemove)
    {
        Builder builder = builder(this);
        return builder.removeElement(toRemove) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata withoutElements(String... toRemove)
    {
        if (toRemove.length == 0)
        {
            return this;
        }
        Builder builder = builder(this);
        return builder.removeElements(toRemove) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata withoutElements(Iterable<? extends String> toRemove)
    {
        Builder builder = builder(this);
        return builder.removeElements(toRemove) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata withoutElements(Predicate<? super ConcreteElementMetadata> predicate)
    {
        Builder builder = builder(this);
        return builder.removeElements(predicate) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata update(Iterable<? extends ConcreteElementMetadata> newElements, Iterable<? extends String> toRemove)
    {
        return update(newElements, getToRemovePredicate(toRemove));
    }

    public ModuleMetadata update(Iterable<? extends ConcreteElementMetadata> newElements, Predicate<? super ConcreteElementMetadata> toRemove)
    {
        return update(indexElements(newElements), toRemove);
    }

    private ModuleMetadata update(MutableMap<String, ConcreteElementMetadata> newElementsByPath, Predicate<? super ConcreteElementMetadata> toRemove)
    {
        if ((newElementsByPath == null) || newElementsByPath.isEmpty())
        {
            if (toRemove != null)
            {
                Builder builder = builder(this);
                if (builder.removeElements(toRemove))
                {
                    return builder.buildNoValidation();
                }
            }
            return this;
        }

        Builder builder = builder(this);
        builder.removeElements(toRemove);
        builder.updateElements(newElementsByPath);
        return builder.build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(String name)
    {
        return builder().withName(name);
    }

    public static Builder builder(int elementCount)
    {
        return new Builder(elementCount);
    }

    public static Builder builder(ModuleMetadata metadata)
    {
        return new Builder(metadata);
    }

    public static class Builder
    {
        private String name;
        private final MutableList<ConcreteElementMetadata> elements;

        private Builder()
        {
            this.elements = Lists.mutable.empty();
        }

        private Builder(int elementCount)
        {
            this.elements = Lists.mutable.ofInitialCapacity(elementCount);
        }

        private Builder(ModuleMetadata metadata)
        {
            this.name = metadata.getName();
            this.elements = Lists.mutable.withAll(metadata.getElements());
        }

        public void setName(String name)
        {
            if ((name != null) && name.isEmpty())
            {
                throw new IllegalArgumentException("name may not be empty");
            }
            this.name = name;
        }

        public void addElement(ConcreteElementMetadata element)
        {
            this.elements.add(Objects.requireNonNull(element, "element metadata may not be null"));
        }

        public void addElements(Iterable<? extends ConcreteElementMetadata> elements)
        {
            elements.forEach(this::addElement);
        }

        public void addElements(ConcreteElementMetadata... elements)
        {
            addElements(Arrays.asList(elements));
        }

        public void updateElement(ConcreteElementMetadata element)
        {
            Objects.requireNonNull(element, "element metadata may not be null");
            int[] count = {0};
            String path = element.getPath();
            this.elements.replaceAll(e -> path.equals(e.getPath()) ? ((count[0]++ == 0) ? element : null) : e);
            if (count[0] == 0)
            {
                this.elements.add(element);
            }
            else if (count[0] > 1)
            {
                this.elements.removeIf(Objects::isNull);
            }
        }

        public void updateElements(Iterable<? extends ConcreteElementMetadata> newElements)
        {
            updateElements(indexElements(newElements));
        }

        private void updateElements(MutableMap<String, ConcreteElementMetadata> newElementsByPath)
        {
            if (newElementsByPath.isEmpty())
            {
                return;
            }

            MutableSet<String> updated = Sets.mutable.empty();
            this.elements.replaceAll(e ->
            {
                String path = e.getPath();
                ConcreteElementMetadata replacement = newElementsByPath.remove(path);
                if (replacement != null)
                {
                    updated.add(path);
                    return replacement;
                }
                return updated.contains(path) ? null : e;
            });
            this.elements.removeIf(Objects::isNull);
            if (newElementsByPath.notEmpty())
            {
                this.elements.addAll(newElementsByPath.values());
            }
        }

        public boolean removeElement(String toRemove)
        {
            return removeElements(getToRemovePredicate(Sets.immutable.with(toRemove)));
        }

        public boolean removeElements(Predicate<? super ConcreteElementMetadata> toRemove)
        {
            return (toRemove != null) && this.elements.removeIf(toRemove::test);
        }

        public boolean removeElements(Iterable<? extends String> toRemove)
        {
            return removeElements(getToRemovePredicate(toRemove));
        }

        public boolean removeElements(String... toRemove)
        {
            switch (toRemove.length)
            {
                case 0:
                {
                    return false;
                }
                case 1:
                {
                    return removeElement(toRemove[0]);
                }
                default:
                {
                    return removeElements(getToRemovePredicate(Sets.immutable.with(toRemove)));
                }
            }
        }

        public Builder withName(String name)
        {
            setName(name);
            return this;
        }

        public Builder withElement(ConcreteElementMetadata element)
        {
            return withElement(element, false);
        }

        public Builder withElement(ConcreteElementMetadata element, boolean update)
        {
            if (update)
            {
                updateElement(element);
            }
            else
            {
                addElement(element);
            }
            return this;
        }

        public Builder withElements(Iterable<? extends ConcreteElementMetadata> elements)
        {
            return withElements(elements, false);
        }

        public Builder withElements(ConcreteElementMetadata... elements)
        {
            return withElements(Arrays.asList(elements));
        }

        public Builder withElements(Iterable<? extends ConcreteElementMetadata> elements, boolean update)
        {
            if (update)
            {
                updateElements(elements);
            }
            else
            {
                addElements(elements);
            }
            return this;
        }

        public Builder withoutElement(String toRemove)
        {
            removeElement(toRemove);
            return this;
        }

        public Builder withoutElements(Predicate<? super ConcreteElementMetadata> toRemove)
        {
            removeElements(toRemove);
            return this;
        }

        public Builder withoutElements(Iterable<? extends String> toRemove)
        {
            removeElements(toRemove);
            return this;
        }

        public Builder withoutElements(String... toRemove)
        {
            removeElements(toRemove);
            return this;
        }

        public ModuleMetadata build()
        {
            Objects.requireNonNull(this.name, "name may not be null");
            sortAndRemoveDuplicateElements();
            validateSourceInfo();
            return buildNoValidation();
        }

        private ModuleMetadata buildNoValidation()
        {
            return new ModuleMetadata(this.name, this.elements.toImmutable());
        }

        private void sortAndRemoveDuplicateElements()
        {
            if (this.elements.size() > 1)
            {
                this.elements.sortThisBy(PackageableElementMetadata::getPath);
                ConcreteElementMetadata[] prev = new ConcreteElementMetadata[1];
                this.elements.removeIf(current ->
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
        }

        private void validateSourceInfo()
        {
            if (this.elements.isEmpty())
            {
                return;
            }

            if (this.elements.size() == 1)
            {
                SourceInformation sourceInfo = elements.get(0).getSourceInformation();
                if (!isSourceInModule(this.name, sourceInfo.getSourceId()))
                {
                    throw new IllegalArgumentException("Invalid source in module '" + this.name + "': " + sourceInfo.getSourceId());
                }
                return;
            }

            MutableList<String> invalidSources = this.elements.collect(e -> e.getSourceInformation().getSourceId(), Sets.mutable.empty()).reject(s -> isSourceInModule(this.name, s), Lists.mutable.empty());
            if (invalidSources.notEmpty())
            {
                StringBuilder builder = new StringBuilder("Invalid source");
                if (invalidSources.size() > 1)
                {
                    invalidSources.sortThis();
                    builder.append('s');
                }
                builder.append(" in module '").append(this.name).append("': ");
                invalidSources.appendString(builder, ", ");
                throw new IllegalArgumentException(builder.toString());
            }
        }
    }

    private static MutableMap<String, ConcreteElementMetadata> indexElements(Iterable<? extends ConcreteElementMetadata> elements)
    {
        if (elements == null)
        {
            return null;
        }

        MutableMap<String, ConcreteElementMetadata> elementsByPath = (elements instanceof Collection) ? Maps.mutable.ofInitialCapacity(((Collection<?>) elements).size()) : Maps.mutable.empty();
        elements.forEach(e ->
        {
            ConcreteElementMetadata old = elementsByPath.put(Objects.requireNonNull(e, "element metadata may not be null").getPath(), e);
            if ((old != null) && !old.equals(e))
            {
                throw new IllegalArgumentException("Conflict for element: " + e.getPath());
            }
        });
        return elementsByPath;
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
