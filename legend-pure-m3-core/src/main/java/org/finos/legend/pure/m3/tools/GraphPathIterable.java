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

package org.finos.legend.pure.m3.tools;

import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.primitive.ObjectBooleanPair;
import org.eclipse.collections.impl.lazy.AbstractLazyIterable;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class GraphPathIterable extends AbstractLazyIterable<GraphPath>
{
    private final ImmutableSet<String> startNodePaths;
    private final ImmutableSet<CoreInstance> startNodes;
    private final SearchFilter searchFilter;
    private final ProcessorSupport processorSupport;

    private GraphPathIterable(Iterable<String> startNodePaths, SearchFilter searchFilter, ProcessorSupport processorSupport)
    {
        this.startNodePaths = Sets.immutable.withAll(startNodePaths);
        this.startNodes = this.startNodePaths.collectWith(GraphPathIterable::getByUserPath, processorSupport);
        this.searchFilter = (searchFilter == null) ? getDefaultSearchFilter() : searchFilter;
        this.processorSupport = processorSupport;
    }

    @Override
    public void each(Procedure<? super GraphPath> procedure)
    {
        for (GraphPath graphPath : this)
        {
            procedure.value(graphPath);
        }
    }

    @Override
    public void forEach(Consumer<? super GraphPath> consumer)
    {
        for (GraphPath graphPath : this)
        {
            consumer.accept(graphPath);
        }
    }

    @Override
    public Iterator<GraphPath> iterator()
    {
        return new GraphPathIterator();
    }

    private boolean isStartPath(GraphPath path)
    {
        return (path.getEdgeCount() == 0) && this.startNodePaths.contains(path.getStartNodePath());
    }

    private boolean isStartNode(CoreInstance node)
    {
        return this.startNodes.contains(node);
    }

    private FilterResult filter(ResolvedGraphPath resolvedGraphPath)
    {
        return this.searchFilter.apply(resolvedGraphPath, this.processorSupport);
    }

    private CoreInstance getByUserPath(String path)
    {
        return getByUserPath(path, this.processorSupport);
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

    private class GraphPathIterator implements Iterator<GraphPath>
    {
        private final MutableSet<GraphPath> visited = Sets.mutable.empty();
        private final Deque<ResolvedGraphPath> deque = new ArrayDeque<>(GraphPathIterable.this.startNodePaths.size());
        private GraphPath next = null;

        private GraphPathIterator()
        {
            for (String startNodePath : GraphPathIterable.this.startNodePaths)
            {
                enqueue(GraphPath.buildPath(startNodePath), Lists.immutable.with(getByUserPath(startNodePath)));
            }
            update();
        }

        @Override
        public boolean hasNext()
        {
            return this.next != null;
        }

        @Override
        public GraphPath next()
        {
            GraphPath path = this.next;
            if (path == null)
            {
                throw new NoSuchElementException();
            }
            update();
            return path;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        private void update()
        {
            GraphPath path = null;
            ObjectBooleanPair<ResolvedGraphPath> resolvedPathAndShouldContinue = getNextToVisit();
            if (resolvedPathAndShouldContinue != null)
            {
                ResolvedGraphPath resolvedPath = resolvedPathAndShouldContinue.getOne();
                path = resolvedPath.getGraphPath();
                boolean shouldContinue = resolvedPathAndShouldContinue.getTwo();
                if (shouldContinue)
                {
                    ImmutableList<CoreInstance> pathNodeList = resolvedPath.getResolvedNodes();
                    CoreInstance finalNode = pathNodeList.getLast();
                    if ((pathNodeList.size() == 1) || !isStartNode(finalNode))
                    {
                        MutableSet<CoreInstance> pathNodeSet = pathNodeList.toSet();
                        for (String key : finalNode.getKeys())
                        {
                            ListIterable<? extends CoreInstance> values = finalNode.getValueForMetaPropertyToMany(key);
                            if (values.size() == 1)
                            {
                                CoreInstance value = values.get(0);
                                if (!pathNodeSet.contains(value))
                                {
                                    enqueue(path.withToOneProperty(key), pathNodeList.newWith(value));
                                }
                            }
                            else if (values.notEmpty())
                            {
                                int i = 0;
                                for (CoreInstance value : values)
                                {
                                    if (!pathNodeSet.contains(value))
                                    {
                                        enqueue(path.withToManyPropertyValueAtIndex(key, i), pathNodeList.newWith(value));
                                    }
                                    i++;
                                }
                            }
                        }
                    }
                }
            }
            this.next = path;
        }

        private void enqueue(GraphPath path, ImmutableList<CoreInstance> resolvedNodes)
        {
            enqueue(new ResolvedGraphPath(path, resolvedNodes));
        }

        private void enqueue(ResolvedGraphPath resolvedGraphPath)
        {
            this.deque.addLast(resolvedGraphPath);
        }

        private ObjectBooleanPair<ResolvedGraphPath> getNextToVisit()
        {
            while (!this.deque.isEmpty())
            {
                ResolvedGraphPath resolvedPath = this.deque.remove();
                if (this.visited.add(resolvedPath.getGraphPath()))
                {
                    if (isStartPath(resolvedPath.getGraphPath()))
                    {
                        return PrimitiveTuples.pair(resolvedPath, true);
                    }
                    switch (filter(resolvedPath))
                    {
                        case ACCEPT_AND_CONTINUE:
                        {
                            return PrimitiveTuples.pair(resolvedPath, true);
                        }
                        case ACCEPT_AND_STOP:
                        {
                            return PrimitiveTuples.pair(resolvedPath, false);
                        }
                    }
                }
            }
            return null;
        }
    }

    public static GraphPathIterable newGraphPathIterable(Iterable<String> startNodePaths, SearchFilter searchFilter, ProcessorSupport processorSupport)
    {
        return new GraphPathIterable(startNodePaths, searchFilter, processorSupport);
    }

    public static GraphPathIterable newGraphPathIterable(Iterable<String> startNodePaths, ProcessorSupport processorSupport)
    {
        return newGraphPathIterable(startNodePaths, null, processorSupport);
    }

    public static GraphPathIterable newGraphPathIterable(Iterable<String> startNodePaths, Iterable<String> properties, int maxPathLength, ProcessorSupport processorSupport)
    {
        SearchFilter filter = getIncludePropertiesFilter(properties);
        if (maxPathLength >= 0)
        {
            filter = joinFilters(getMaxPathLengthFilter(maxPathLength), filter);
        }
        return newGraphPathIterable(startNodePaths, filter, processorSupport);
    }

    public static GraphPathIterable newGraphPathIterable(Iterable<String> startNodePaths, Predicate<? super CoreInstance> shouldStopAtNode, int maxPathLength, ProcessorSupport processorSupport)
    {
        SearchFilter filter = getStopAtNodeFilter(shouldStopAtNode);
        if (maxPathLength >= 0)
        {
            filter = joinFilters(getMaxPathLengthFilter(maxPathLength), filter);
        }
        return newGraphPathIterable(startNodePaths, filter, processorSupport);
    }

    public static GraphPathIterable newGraphPathIterable(Iterable<String> startNodePaths, int maxPathLength, ProcessorSupport processorSupport)
    {
        return newGraphPathIterable(startNodePaths, getMaxPathLengthFilter(maxPathLength), processorSupport);
    }

    public static SearchFilter getStopAtNodeFilter(Predicate<? super CoreInstance> shouldStopAtNode)
    {
        return (resolvedGraphPath, processorSupport) -> shouldStopAtNode.test(resolvedGraphPath.getLastResolvedNode()) ? FilterResult.ACCEPT_AND_STOP : FilterResult.ACCEPT_AND_CONTINUE;
    }

    public static SearchFilter getMaxPathLengthFilter(int maxPathLength)
    {
        return (resolvedGraphPath, processorSupport) ->
        {
            int pathLength = resolvedGraphPath.getGraphPath().getEdgeCount();
            return (pathLength > maxPathLength) ? FilterResult.REJECT : ((pathLength == maxPathLength) ? FilterResult.ACCEPT_AND_STOP : FilterResult.ACCEPT_AND_CONTINUE);
        };
    }

    public static SearchFilter getIncludePropertiesFilter(Iterable<String> includedProperties)
    {
        Set<String> includedPropertiesSet = (includedProperties instanceof Set) ? (Set<String>) includedProperties : Sets.mutable.withAll(includedProperties);
        return getPropertiesFilter(includedPropertiesSet::contains);
    }

    public static SearchFilter getExcludePropertiesFilter(Iterable<String> excludedProperties)
    {
        Set<String> excludedPropertiesSet = (excludedProperties instanceof Set) ? (Set<String>) excludedProperties : Sets.mutable.withAll(excludedProperties);
        return getPropertiesFilter(p -> !excludedPropertiesSet.contains(p));
    }

    public static SearchFilter getPropertiesFilter(Predicate<? super String> propertyPredicate)
    {
        return (resolvedGraphPath, processorSupport) -> resolvedGraphPath.getGraphPath().getEdges().allSatisfy(e -> propertyPredicate.test(e.getProperty())) ? FilterResult.ACCEPT_AND_CONTINUE : FilterResult.REJECT;
    }

    public static SearchFilter joinFilters(SearchFilter... filters)
    {
        return joinFilters(ArrayAdapter.adapt(filters));
    }

    public static SearchFilter joinFilters(Iterable<? extends SearchFilter> filters)
    {
        MutableList<SearchFilter> flattenedFilters = flattenFilters(filters);
        return (flattenedFilters.size() == 1) ? flattenedFilters.get(0) : new JoinSearchFilter(flattenedFilters.toImmutable());
    }

    public static SearchFilter getDefaultSearchFilter()
    {
        return GraphPathIterable::defaultSearchFilter;
    }

    private static FilterResult defaultSearchFilter(ResolvedGraphPath resolvedGraphPath, ProcessorSupport processorSupport)
    {
        // Don't traverse to an element's package
        if (resolvedGraphPath.getGraphPath().getEdges().anySatisfy(e -> M3Properties._package.equals(e.getProperty())))
        {
            return FilterResult.REJECT;
        }

        // Stop at packaged or top level nodes
        CoreInstance lastNode = resolvedGraphPath.getLastResolvedNode();
        if ((lastNode.getValueForMetaPropertyToOne(M3Properties._package) instanceof Package) || (lastNode == processorSupport.repository_getTopLevel(lastNode.getName())))
        {
            return FilterResult.ACCEPT_AND_STOP;
        }

        // Otherwise continue
        return FilterResult.ACCEPT_AND_CONTINUE;
    }

    public static class ResolvedGraphPath
    {
        private final GraphPath path;
        private final ImmutableList<CoreInstance> resolvedNodes;

        private ResolvedGraphPath(GraphPath path, ImmutableList<CoreInstance> resolvedNodes)
        {
            this.path = path;
            this.resolvedNodes = resolvedNodes;
        }

        public GraphPath getGraphPath()
        {
            return this.path;
        }

        public ImmutableList<CoreInstance> getResolvedNodes()
        {
            return this.resolvedNodes;
        }

        public CoreInstance getLastResolvedNode()
        {
            return this.resolvedNodes.getLast();
        }
    }

    public interface SearchFilter extends BiFunction<ResolvedGraphPath, ProcessorSupport, FilterResult>
    {
    }

    public enum FilterResult
    {
        ACCEPT_AND_CONTINUE,
        ACCEPT_AND_STOP,
        REJECT
    }

    private static MutableList<SearchFilter> flattenFilters(Iterable<? extends SearchFilter> searchFilters)
    {
        MutableList<SearchFilter> result = Lists.mutable.empty();
        searchFilters.forEach(filter ->
        {
            if (filter instanceof JoinSearchFilter)
            {
                result.addAll(((JoinSearchFilter) filter).searchFilters.castToList());
            }
            else
            {
                result.add(filter);
            }
        });
        return result;
    }

    private static class JoinSearchFilter implements SearchFilter
    {
        private final ImmutableList<SearchFilter> searchFilters;

        private JoinSearchFilter(ImmutableList<SearchFilter> searchFilters)
        {
            this.searchFilters = searchFilters;
        }

        @Override
        public FilterResult apply(ResolvedGraphPath resolvedGraphPath, ProcessorSupport processorSupport)
        {
            FilterResult result = FilterResult.ACCEPT_AND_CONTINUE;
            for (SearchFilter searchFilter : this.searchFilters)
            {
                switch (searchFilter.apply(resolvedGraphPath, processorSupport))
                {
                    case REJECT:
                    {
                        return FilterResult.REJECT;
                    }
                    case ACCEPT_AND_STOP:
                    {
                        result = FilterResult.ACCEPT_AND_STOP;
                        break;
                    }
                }
            }
            return result;
        }
    }
}
