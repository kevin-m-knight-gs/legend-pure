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

package org.finos.legend.pure.m3.navigation.graph;

import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.lazy.AbstractLazyIterable;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.GraphWalkFilterResult;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GraphPathIterable extends AbstractLazyIterable<ResolvedGraphPath>
{
    private final MapIterable<String, CoreInstance> startNodesByPath;
    private final SetIterable<CoreInstance> startNodes;
    private final Function<? super ResolvedGraphPath, ? extends GraphWalkFilterResult> pathFilter;
    private final BiPredicate<? super ResolvedGraphPath, ? super String> propertyFilter;

    private GraphPathIterable(MapIterable<String, CoreInstance> startNodesByPath, Function<? super ResolvedGraphPath, ? extends GraphWalkFilterResult> pathFilter, BiPredicate<? super ResolvedGraphPath, ? super String> propertyFilter)
    {
        this.startNodesByPath = startNodesByPath;
        this.startNodes = Sets.immutable.withAll(this.startNodesByPath.valuesView());
        this.pathFilter = (pathFilter == null) ? rgp -> GraphWalkFilterResult.ACCEPT_AND_CONTINUE : pathFilter;
        this.propertyFilter = (propertyFilter == null) ? (rgp, p) -> true : propertyFilter;
    }

    @Override
    public void each(Procedure<? super ResolvedGraphPath> procedure)
    {
        forEach((Consumer<? super ResolvedGraphPath>) procedure);
    }

    @Override
    public void forEach(Consumer<? super ResolvedGraphPath> consumer)
    {
        spliterator().forEachRemaining(consumer);
    }

    @Override
    public boolean isEmpty()
    {
        return this.startNodesByPath.isEmpty();
    }

    @Override
    public ResolvedGraphPath getAny()
    {
        Pair<String, CoreInstance> pathNode = this.startNodesByPath.keyValuesView().getAny();
        return (pathNode == null) ? null : new ResolvedGraphPath(GraphPath.buildPath(pathNode.getOne()), Lists.immutable.with(pathNode.getTwo()));
    }

    @Override
    public ResolvedGraphPath getFirst()
    {
        return isEmpty() ? null : stream().findFirst().orElse(null);
    }

    @Override
    public ResolvedGraphPath detect(org.eclipse.collections.api.block.predicate.Predicate<? super ResolvedGraphPath> predicate)
    {
        return detectOptional(predicate).orElse(null);
    }

    @Override
    public Optional<ResolvedGraphPath> detectOptional(org.eclipse.collections.api.block.predicate.Predicate<? super ResolvedGraphPath> predicate)
    {
        return stream().filter(predicate).findFirst();
    }

    @Override
    public boolean anySatisfy(org.eclipse.collections.api.block.predicate.Predicate<? super ResolvedGraphPath> predicate)
    {
        return stream().anyMatch(predicate);
    }

    @Override
    public boolean allSatisfy(org.eclipse.collections.api.block.predicate.Predicate<? super ResolvedGraphPath> predicate)
    {
        return stream().anyMatch(predicate);
    }

    @Override
    public boolean noneSatisfy(Predicate<? super ResolvedGraphPath> predicate)
    {
        return stream().noneMatch(predicate);
    }

    @Override
    public Iterator<ResolvedGraphPath> iterator()
    {
        return Spliterators.iterator(spliterator());
    }

    @Override
    public Spliterator<ResolvedGraphPath> spliterator()
    {
        return new ResolvedGraphPathSpliterator(this.startNodesByPath);
    }

    public Stream<ResolvedGraphPath> stream()
    {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<ResolvedGraphPath> parallelStream()
    {
        return StreamSupport.stream(spliterator(), true);
    }

    private boolean isStartNode(CoreInstance node)
    {
        return this.startNodes.contains(node);
    }

    private GraphWalkFilterResult filterPath(ResolvedGraphPath resolvedGraphPath)
    {
        return this.pathFilter.apply(resolvedGraphPath);
    }

    private boolean filterProperty(ResolvedGraphPath resolvedGraphPath, String property)
    {
        return this.propertyFilter.test(resolvedGraphPath, property);
    }

    private class ResolvedGraphPathSpliterator implements Spliterator<ResolvedGraphPath>
    {
        private final Deque<SearchNode> deque;

        private ResolvedGraphPathSpliterator(Deque<SearchNode> deque)
        {
            this.deque = deque;
        }

        private ResolvedGraphPathSpliterator(MapIterable<String, CoreInstance> nodesByPath)
        {
            this(new ArrayDeque<>(nodesByPath.size()));
            nodesByPath.forEachKeyValue((path, node) -> enqueue(new ResolvedGraphPath(GraphPath.buildPath(path), Lists.immutable.with(node)), true));
        }

        @Override
        public boolean tryAdvance(Consumer<? super ResolvedGraphPath> action)
        {
            if (this.deque.isEmpty())
            {
                return false;
            }

            SearchNode node = this.deque.pollFirst();
            ResolvedGraphPath resolvedPath = node.resolvedGraphPath;
            if (node.shouldContinue)
            {
                continueFromPath(resolvedPath);
            }
            action.accept(resolvedPath);
            return true;
        }

        @Override
        public Spliterator<ResolvedGraphPath> trySplit()
        {
            if (this.deque.size() < 2)
            {
                return null;
            }

            int splitSize = this.deque.size() / 2;
            Deque<SearchNode> newDeque = new ArrayDeque<>(splitSize);
            for (int i = 0; i < splitSize; i++)
            {
                newDeque.addFirst(this.deque.pollLast());
            }
            return new ResolvedGraphPathSpliterator(newDeque);
        }

        @Override
        public long estimateSize()
        {
            return this.deque.isEmpty() ? 0L : Long.MAX_VALUE;
        }

        @Override
        public long getExactSizeIfKnown()
        {
            return this.deque.isEmpty() ? 0L : -1L;
        }

        @Override
        public int characteristics()
        {
            return DISTINCT | NONNULL;
        }

        private void continueFromPath(ResolvedGraphPath resolvedPath)
        {
            GraphPath path = resolvedPath.getGraphPath();
            ImmutableList<CoreInstance> pathNodeList = resolvedPath.getResolvedNodes();
            CoreInstance finalNode = pathNodeList.getLast();
            Collection<CoreInstance> pathNodeSet = (pathNodeList.size() > 8) ? pathNodeList.toSet() : pathNodeList.castToList();
            finalNode.getKeys().forEach(key ->
            {
                if (filterProperty(resolvedPath, key))
                {
                    ListIterable<? extends CoreInstance> values = finalNode.getValueForMetaPropertyToMany(key);
                    if (values.size() == 1)
                    {
                        CoreInstance value = values.get(0);
                        if (!isStartNode(value) && !pathNodeSet.contains(value))
                        {
                            possiblyEnqueue(path.withToOneProperty(key), pathNodeList.newWith(value));
                        }
                    }
                    else if (values.notEmpty())
                    {
                        values.forEachWithIndex((value, i) ->
                        {
                            if (!isStartNode(value) && !pathNodeSet.contains(value))
                            {
                                possiblyEnqueue(path.withToManyPropertyValueAtIndex(key, i), pathNodeList.newWith(value));
                            }
                        });
                    }
                }
            });
        }

        private void possiblyEnqueue(GraphPath path, ImmutableList<CoreInstance> resolvedNodes)
        {
            ResolvedGraphPath resolvedGraphPath = new ResolvedGraphPath(path, resolvedNodes);
            GraphWalkFilterResult filterResult = filterPath(resolvedGraphPath);
            if (filterResult.shouldAccept())
            {
                enqueue(resolvedGraphPath, filterResult.shouldContinue());
            }
        }

        private void enqueue(ResolvedGraphPath resolvedGraphPath, boolean shouldContinue)
        {
            this.deque.addLast(new SearchNode(resolvedGraphPath, shouldContinue));
        }
    }

    private static class SearchNode
    {
        private final ResolvedGraphPath resolvedGraphPath;
        private final boolean shouldContinue;

        private SearchNode(ResolvedGraphPath resolvedGraphPath, boolean shouldContinue)
        {
            this.resolvedGraphPath = resolvedGraphPath;
            this.shouldContinue = shouldContinue;
        }
    }

    public static GraphPathIterable build(String startNodePath, Function<? super ResolvedGraphPath, ? extends GraphWalkFilterResult> pathFilter, BiPredicate<? super ResolvedGraphPath, ? super String> propertyFilter, ProcessorSupport processorSupport)
    {
        return new GraphPathIterable(Maps.immutable.with(startNodePath, getByUserPath(startNodePath, processorSupport)), pathFilter, propertyFilter);
    }

    public static GraphPathIterable build(CoreInstance startNode, Function<? super ResolvedGraphPath, ? extends GraphWalkFilterResult> pathFilter, BiPredicate<? super ResolvedGraphPath, ? super String> propertyFilter, ProcessorSupport processorSupport)
    {
        if (!GraphPath.isPackagedOrTopLevel(startNode, processorSupport))
        {
            throw new IllegalArgumentException("Invalid start node: " + startNode);
        }
        return new GraphPathIterable(Maps.immutable.with(PackageableElement.getUserPathForPackageableElement(startNode), startNode), pathFilter, propertyFilter);
    }

    public static Builder builder(ProcessorSupport processorSupport)
    {
        return new Builder(processorSupport);
    }

    private static CoreInstance getByUserPath(String path, ProcessorSupport processorSupport)
    {
        CoreInstance node = processorSupport.package_getByUserPath(path);
        if (node == null)
        {
            throw new IllegalArgumentException("Unknown path: " + path);
        }
        return node;
    }

    public static class Builder
    {
        private final MutableMap<String, CoreInstance> startNodesByPath = Maps.mutable.empty();
        private Function<? super ResolvedGraphPath, ? extends GraphWalkFilterResult> pathFilter = null;
        private BiPredicate<? super ResolvedGraphPath, ? super String> propertyFilter = null;
        private final ProcessorSupport processorSupport;

        private Builder(ProcessorSupport processorSupport)
        {
            this.processorSupport = processorSupport;
        }

        public Builder withStartNode(CoreInstance element)
        {
            if (!GraphPath.isPackagedOrTopLevel(element, this.processorSupport))
            {
                throw new IllegalArgumentException("Invalid start node: " + element);
            }
            String path = PackageableElement.getUserPathForPackageableElement(element);
            this.startNodesByPath.getIfAbsentPut(path, element);
            return this;
        }

        public Builder withStartNodes(CoreInstance... elements)
        {
            return withStartNodes(Arrays.asList(elements));
        }

        public Builder withStartNodes(Iterable<? extends CoreInstance> elements)
        {
            elements.forEach(this::withStartNode);
            return this;
        }

        public Builder withStartNodePath(String path)
        {
            this.startNodesByPath.getIfAbsentPut(path, () -> getByUserPath(path, this.processorSupport));
            return this;
        }

        public Builder withStartNodePaths(String... paths)
        {
            return withStartNodePaths(Arrays.asList(paths));
        }

        public Builder withStartNodePaths(Iterable<String> paths)
        {
            paths.forEach(this::withStartNodePath);
            return this;
        }

        public Builder withPathFilter(Function<? super ResolvedGraphPath, ? extends GraphWalkFilterResult> pathFilter)
        {
            this.pathFilter = pathFilter;
            return this;
        }

        public Builder withPropertyFilter(BiPredicate<? super ResolvedGraphPath, ? super String> propertyFilter)
        {
            this.propertyFilter = propertyFilter;
            return this;
        }

        public GraphPathIterable build()
        {
            return new GraphPathIterable(this.startNodesByPath.toImmutable(), this.pathFilter, this.propertyFilter);
        }
    }
}
