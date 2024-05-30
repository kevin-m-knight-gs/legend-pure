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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.finos.legend.pure.m3.serialization.compiler.ModuleHelper;
import org.finos.legend.pure.m3.tools.ListHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ModuleMetadata
{
    private final String name;
    private final ImmutableList<ConcreteElementMetadata> elements;
    private final ImmutableList<SourceMetadata> sources;

    private ModuleMetadata(String name, ImmutableList<ConcreteElementMetadata> elements, ImmutableList<SourceMetadata> sources)
    {
        this.name = name;
        this.elements = elements;
        this.sources = sources;
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

    public int getSourceCount()
    {
        return this.sources.size();
    }

    public ImmutableList<SourceMetadata> getSources()
    {
        return this.sources;
    }

    public void forEachSource(Consumer<? super SourceMetadata> consumer)
    {
        this.sources.forEach(consumer);
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
        return this.name.equals(that.name) &&
                this.elements.equals(that.elements) &&
                this.sources.equals(that.sources);
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
                .append(" name='").append(this.name).append("' elements=[");
        if (this.elements.notEmpty())
        {
            this.elements.forEach(e -> e.appendString(builder).append(", "));
            builder.setLength(builder.length() - 2);
        }
        builder.append("] sources=[");
        this.sources.forEach(source ->
        {
            builder.append(source.getSourceId()).append('{');
            source.getSections().forEach(section -> section.getElements().appendString(builder.append(section.getParser()), ":[", ", ", "], "));
            if (source.getSections().notEmpty())
            {
                builder.setLength(builder.length() - 2);
            }
            builder.append("}, ");
        });
        if (this.sources.notEmpty())
        {
            builder.setLength(builder.length() - 2);
        }
        return builder.append("]>").toString();
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

    public ModuleMetadata updateElements(Iterable<? extends ConcreteElementMetadata> newElements, Iterable<? extends String> toRemove)
    {
        return update(newElements, toRemove, null, null);
    }

    public ModuleMetadata updateElements(Iterable<? extends ConcreteElementMetadata> newElements, Predicate<? super ConcreteElementMetadata> toRemove)
    {
        return update(newElements, toRemove, null, null);
    }

    public ModuleMetadata withSource(SourceMetadata newSource)
    {
        return builder(this).withSource(newSource, true).build();
    }

    public ModuleMetadata withSources(SourceMetadata... newSources)
    {
        return withSources(Arrays.asList(newSources));
    }

    public ModuleMetadata withSources(Iterable<? extends SourceMetadata> newSources)
    {
        return builder(this).withSources(newSources, true).build();
    }

    public ModuleMetadata withoutSource(String toRemove)
    {
        Builder builder = builder(this);
        return builder.removeSource(toRemove) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata withoutSources(String... toRemove)
    {
        if (toRemove.length == 0)
        {
            return this;
        }
        Builder builder = builder(this);
        return builder.removeSources(toRemove) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata withoutSources(Iterable<? extends String> toRemove)
    {
        Builder builder = builder(this);
        return builder.removeSources(toRemove) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata withoutSources(Predicate<? super SourceMetadata> predicate)
    {
        Builder builder = builder(this);
        return builder.removeSources(predicate) ? builder.buildNoValidation() : this;
    }

    public ModuleMetadata updateSources(Iterable<? extends SourceMetadata> newSources, Iterable<? extends String> toRemove)
    {
        return update(null, null, newSources, toRemove);
    }

    public ModuleMetadata updateSources(Iterable<? extends SourceMetadata> newSources, Predicate<? super SourceMetadata> toRemove)
    {
        return update(null, null, newSources, toRemove);
    }

    public ModuleMetadata update(Iterable<? extends ConcreteElementMetadata> newElements, Iterable<? extends String> elementsToRemove, Iterable<? extends SourceMetadata> newSources, Iterable<? extends String> sourcesToRemove)
    {
        return update(newElements, getRemoveElementPredicate(elementsToRemove), newSources, getRemoveSourcePredicate(sourcesToRemove));
    }

    public ModuleMetadata update(Iterable<? extends ConcreteElementMetadata> newElements, Predicate<? super ConcreteElementMetadata> removeElement, Iterable<? extends SourceMetadata> newSources, Predicate<? super SourceMetadata> removeSource)
    {
        return update(indexElements(newElements), removeElement, indexSources(newSources), removeSource);
    }

    private ModuleMetadata update(MutableMap<String, ConcreteElementMetadata> newElementsByPath, Predicate<? super ConcreteElementMetadata> removeElement, MutableMap<String, SourceMetadata> newSourcesById, Predicate<? super SourceMetadata> removeSource)
    {
        if (((newElementsByPath == null) || newElementsByPath.isEmpty()) && (((newSourcesById == null) || newSourcesById.isEmpty())))
        {
            if ((removeElement != null) || (removeSource != null))
            {
                Builder builder = builder(this);
                boolean modifiedElements = builder.removeElements(removeElement);
                boolean modifiedSources = builder.removeSources(removeSource);
                if (modifiedElements || modifiedSources)
                {
                    return builder.buildNoValidation();
                }
            }
            return this;
        }

        Builder builder = builder(this);
        builder.removeElements(removeElement);
        builder.removeSources(removeSource);
        if ((newElementsByPath != null) && newElementsByPath.notEmpty())
        {
            builder.updateElements(newElementsByPath);
        }
        if ((newSourcesById != null) && newSourcesById.notEmpty())
        {
            builder.updateSources(newSourcesById);
        }
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

    public static Builder builder(int elementCount, int sourceCount)
    {
        return new Builder(elementCount, sourceCount);
    }

    public static Builder builder(ModuleMetadata metadata)
    {
        return new Builder(metadata);
    }

    public static class Builder
    {
        private String name;
        private final MutableList<ConcreteElementMetadata> elements;
        private final MutableList<SourceMetadata> sources;

        private Builder()
        {
            this.elements = Lists.mutable.empty();
            this.sources = Lists.mutable.empty();
        }

        private Builder(int elementCount, int sourceCount)
        {
            this.elements = Lists.mutable.ofInitialCapacity(elementCount);
            this.sources = Lists.mutable.ofInitialCapacity(sourceCount);
        }

        private Builder(ModuleMetadata metadata)
        {
            this.name = metadata.getName();
            this.elements = Lists.mutable.withAll(metadata.getElements());
            this.sources = Lists.mutable.withAll(metadata.getSources());
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
            return removeElements(getRemoveElementPredicate(Sets.immutable.with(toRemove)));
        }

        public boolean removeElements(Iterable<? extends String> toRemove)
        {
            return removeElements(getRemoveElementPredicate(toRemove));
        }

        public boolean removeElements(String... toRemove)
        {
            return (toRemove.length != 0) && removeElements(Sets.immutable.with(toRemove));
        }

        public boolean removeElements(Predicate<? super ConcreteElementMetadata> toRemove)
        {
            return (toRemove != null) && this.elements.removeIf(toRemove::test);
        }

        public void addSource(SourceMetadata source)
        {
            this.sources.add(Objects.requireNonNull(source, "source metadata may not be null"));
        }

        public void addSources(Iterable<? extends SourceMetadata> sources)
        {
            sources.forEach(this::addSource);
        }

        public void addSources(SourceMetadata... sources)
        {
            addSources(Arrays.asList(sources));
        }

        public void updateSource(SourceMetadata source)
        {
            Objects.requireNonNull(source, "source metadata may not be null");
            int[] count = {0};
            String sourceId = source.getSourceId();
            this.sources.replaceAll(s -> sourceId.equals(s.getSourceId()) ? ((count[0]++ == 0) ? source : null) : s);
            if (count[0] == 0)
            {
                this.sources.add(source);
            }
            else if (count[0] > 1)
            {
                this.sources.removeIf(Objects::isNull);
            }
        }

        public void updateSources(Iterable<? extends SourceMetadata> newSources)
        {
            updateSources(indexSources(newSources));
        }

        private void updateSources(MutableMap<String, SourceMetadata> newSourcesById)
        {
            if (newSourcesById.isEmpty())
            {
                return;
            }

            MutableSet<String> updated = Sets.mutable.empty();
            this.sources.replaceAll(s ->
            {
                String sourceId = s.getSourceId();
                SourceMetadata replacement = newSourcesById.remove(sourceId);
                if (replacement != null)
                {
                    updated.add(sourceId);
                    return replacement;
                }
                return updated.contains(sourceId) ? null : s;
            });
            this.sources.removeIf(Objects::isNull);
            if (newSourcesById.notEmpty())
            {
                this.sources.addAll(newSourcesById.values());
            }
        }

        public boolean removeSource(String toRemove)
        {
            return removeSources(getRemoveSourcePredicate(Sets.immutable.with(toRemove)));
        }

        public boolean removeSources(Iterable<? extends String> toRemove)
        {
            return removeSources(getRemoveSourcePredicate(toRemove));
        }

        public boolean removeSources(String... toRemove)
        {
            return (toRemove.length != 0) && removeSources(Sets.immutable.with(toRemove));
        }

        public boolean removeSources(Predicate<? super SourceMetadata> toRemove)
        {
            return (toRemove != null) && this.sources.removeIf(toRemove);
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

        public Builder withSource(SourceMetadata source)
        {
            return withSource(source, false);
        }

        public Builder withSource(SourceMetadata source, boolean update)
        {
            if (update)
            {
                updateSource(source);
            }
            else
            {
                addSource(source);
            }
            return this;
        }

        public Builder withSources(Iterable<? extends SourceMetadata> sources)
        {
            return withSources(sources, false);
        }

        public Builder withSources(SourceMetadata... sources)
        {
            return withSources(Arrays.asList(sources));
        }

        public Builder withSources(Iterable<? extends SourceMetadata> sources, boolean update)
        {
            if (update)
            {
                updateSources(sources);
            }
            else
            {
                addSources(sources);
            }
            return this;
        }

        public Builder withoutSource(String toRemove)
        {
            removeSource(toRemove);
            return this;
        }

        public Builder withoutSources(Predicate<? super SourceMetadata> toRemove)
        {
            removeSources(toRemove);
            return this;
        }

        public Builder withoutSources(Iterable<? extends String> toRemove)
        {
            removeSources(toRemove);
            return this;
        }

        public Builder withoutSources(String... toRemove)
        {
            removeSources(toRemove);
            return this;
        }

        public ModuleMetadata build()
        {
            Objects.requireNonNull(this.name, "name may not be null");
            sortAndRemoveDuplicates(this.elements, PackageableElementMetadata::getPath, "element");
            sortAndRemoveDuplicates(this.sources, SourceMetadata::getSourceId, "source");
            validateSources();
            return buildNoValidation();
        }

        private ModuleMetadata buildNoValidation()
        {
            return new ModuleMetadata(this.name, this.elements.toImmutable(), this.sources.toImmutable());
        }

        private void validateSources()
        {
            MutableList<String> invalidSourceIds = Lists.mutable.empty();
            this.elements.collectIf(
                    e -> !ModuleHelper.isSourceInModule(e.getSourceInformation(), this.name),
                    e -> e.getSourceInformation().getSourceId(),
                    invalidSourceIds);
            this.sources.collectIf(
                    s -> !ModuleHelper.isSourceInModule(s.getSourceId(), this.name),
                    SourceMetadata::getSourceId,
                    invalidSourceIds);
            if (invalidSourceIds.notEmpty())
            {
                MutableSet<String> set = Sets.mutable.empty();
                invalidSourceIds.removeIf(s -> !set.add(s));
                StringBuilder builder = new StringBuilder("Invalid source");
                if (invalidSourceIds.size() > 1)
                {
                    invalidSourceIds.sortThis();
                    builder.append('s');
                }
                builder.append(" in module '").append(this.name).append("': ");
                invalidSourceIds.appendString(builder, ", ");
                throw new IllegalArgumentException(builder.toString());
            }
        }

        private static <T, V extends Comparable<? super V>> void sortAndRemoveDuplicates(MutableList<T> list, Function<T, V> keyFn, String description)
        {
            ListHelper.sortAndRemoveDuplicates(list, Comparator.comparing(keyFn), (previous, current) ->
            {
                V key = keyFn.apply(current);
                if (!Objects.equals(key, keyFn.apply(previous)))
                {
                    return false;
                }
                if (!previous.equals(current))
                {
                    throw new IllegalArgumentException("Conflict for " + description + ": " + key);
                }
                return true;
            });
        }
    }

    private static MutableMap<String, ConcreteElementMetadata> indexElements(Iterable<? extends ConcreteElementMetadata> elements)
    {
        return indexBy(elements, ConcreteElementMetadata::getPath, "element");
    }

    private static MutableMap<String, SourceMetadata> indexSources(Iterable<? extends SourceMetadata> sources)
    {
        return indexBy(sources, SourceMetadata::getSourceId, "source");
    }

    private static <T> MutableMap<String, T> indexBy(Iterable<? extends T> iterable, Function<T, ? extends String> keyFn, String description)
    {
        if (iterable == null)
        {
            return null;
        }

        MutableMap<String, T> index = (iterable instanceof Collection) ? Maps.mutable.ofInitialCapacity(((Collection<?>) iterable).size()) : Maps.mutable.empty();
        iterable.forEach(object ->
        {
            String key = keyFn.apply(Objects.requireNonNull(object, description + " metadata may not be null"));
            T old = index.put(key, object);
            if ((old != null) && !old.equals(object))
            {
                throw new IllegalArgumentException("Conflict for " + description + ": " + key);
            }
        });
        return index;
    }

    private static Predicate<ConcreteElementMetadata> getRemoveElementPredicate(Iterable<? extends String> toRemove)
    {
        if (toRemove == null)
        {
            return null;
        }
        SetIterable<? extends String> set = toSetIterable(toRemove);
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

    private static Predicate<SourceMetadata> getRemoveSourcePredicate(Iterable<? extends String> toRemove)
    {
        if (toRemove == null)
        {
            return null;
        }
        SetIterable<? extends String> set = toSetIterable(toRemove);
        switch (set.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                String sourceToRemove = set.getAny();
                return (sourceToRemove == null) ? null : smd -> sourceToRemove.equals(smd.getSourceId());
            }
            default:
            {
                return smd -> set.contains(smd.getSourceId());
            }
        }
    }

    private static SetIterable<? extends String> toSetIterable(Iterable<? extends String> iterable)
    {
        if (iterable instanceof SetIterable)
        {
            return (SetIterable<? extends String>) iterable;
        }
        if (iterable instanceof Set)
        {
            return SetAdapter.adapt((Set<? extends String>) iterable);
        }
        return Sets.mutable.withAll(iterable);
    }
}
