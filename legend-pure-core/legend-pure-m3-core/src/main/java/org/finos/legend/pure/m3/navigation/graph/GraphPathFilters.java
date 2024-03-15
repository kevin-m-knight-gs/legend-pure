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

package org.finos.legend.pure.m3.navigation.graph;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class GraphPathFilters
{
    public static GraphPathFilter getPathLengthFilter(IntFunction<? extends GraphPathFilterResult> lengthFunction)
    {
        return resolvedGraphPath -> lengthFunction.apply(resolvedGraphPath.getGraphPath().getEdgeCount());
    }

    public static GraphPathFilter getMaxPathLengthFilter(int maxPathLength)
    {
        return getPathLengthFilter(l -> (l > maxPathLength) ? GraphPathFilterResult.REJECT : ((l == maxPathLength) ? GraphPathFilterResult.ACCEPT_AND_STOP : GraphPathFilterResult.ACCEPT_AND_CONTINUE));
    }

    public static GraphPathFilter getStopAtNodeFilter(CoreInstance node)
    {
        return getStopAtNodeFilter(node::equals);
    }

    public static GraphPathFilter getStopAtNodeFilter(CoreInstance... nodes)
    {
        return (nodes.length == 1) ? getStopAtNodeFilter(nodes[0]) : getStopAtNodeFilter(Sets.mutable.with(nodes));
    }

    public static GraphPathFilter getStopAtNodeFilter(Iterable<? extends CoreInstance> nodes)
    {
        Set<? extends CoreInstance> set = (nodes instanceof Set) ? (Set<? extends CoreInstance>) nodes : Sets.mutable.withAll(nodes);
        return getStopAtNodeFilter(set::contains);
    }

    public static GraphPathFilter getStopAtNodeFilter(Predicate<? super CoreInstance> predicate)
    {
        return resolvedGraphPath -> predicate.test(resolvedGraphPath.getLastResolvedNode()) ? GraphPathFilterResult.ACCEPT_AND_STOP : GraphPathFilterResult.ACCEPT_AND_CONTINUE;
    }

    public static GraphPathFilter getStopAtPackagedOrTopLevel(ProcessorSupport processorSupport)
    {
        return rgp -> GraphPath.isPackagedOrTopLevel(rgp.getLastResolvedNode(), processorSupport) ? GraphPathFilterResult.ACCEPT_AND_STOP : GraphPathFilterResult.ACCEPT_AND_CONTINUE;
    }

    public static GraphPathFilter combinePathFilters(Iterable<? extends Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult>> filters)
    {
        return builder().withFilters(filters).build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private final MutableList<Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult>> filters = Lists.mutable.empty();

        private Builder()
        {
        }

        public Builder withMaxPathLength(int length)
        {
            return withFilter(getMaxPathLengthFilter(length));
        }

        public Builder stopAtNode(CoreInstance node)
        {
            return withFilter(getStopAtNodeFilter(node));
        }

        public Builder stopAtNode(CoreInstance... nodes)
        {
            return withFilter(getStopAtNodeFilter(nodes));
        }

        public Builder stopAtNode(Iterable<? extends CoreInstance> nodes)
        {
            return withFilter(getStopAtNodeFilter(nodes));
        }

        public Builder stopAtNode(Predicate<? super CoreInstance> predicate)
        {
            return withFilter(getStopAtNodeFilter(predicate));
        }

        public Builder stopAtPackagedOrTopLevel(ProcessorSupport processorSupport)
        {
            return withFilter(getStopAtPackagedOrTopLevel(processorSupport));
        }

        public Builder withFilter(Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult> filter)
        {
            if (filter instanceof CombinedPathFilter)
            {
                this.filters.addAll(((CombinedPathFilter) filter).filters.castToList());
            }
            else
            {
                this.filters.add(Objects.requireNonNull(filter));
            }
            return this;
        }

        public Builder withFilters(Iterable<? extends Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult>> filters)
        {
            filters.forEach(this::withFilter);
            return this;
        }

        public GraphPathFilter build()
        {
            switch (this.filters.size())
            {
                case 0:
                {
                    return rgp -> GraphPathFilterResult.ACCEPT_AND_CONTINUE;
                }
                case 1:
                {
                    Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult> filter = this.filters.get(0);
                    return (filter instanceof GraphPathFilter) ? (GraphPathFilter) filter : filter::apply;
                }
                default:
                {
                    return new CombinedPathFilter(this.filters.toImmutable());
                }
            }
        }
    }

    private static final class CombinedPathFilter implements GraphPathFilter
    {
        private final ImmutableList<? extends Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult>> filters;

        private CombinedPathFilter(ImmutableList<? extends Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult>> filters)
        {
            this.filters = filters;
        }

        @Override
        public GraphPathFilterResult apply(ResolvedGraphPath resolvedGraphPath)
        {
            GraphPathFilterResult result = GraphPathFilterResult.ACCEPT_AND_CONTINUE;
            for (Function<? super ResolvedGraphPath, ? extends GraphPathFilterResult> filter : this.filters)
            {
                GraphPathFilterResult filterResult = filter.apply(resolvedGraphPath);
                if (filterResult != null)
                {
                    switch (filterResult)
                    {
                        case REJECT:
                        {
                            return GraphPathFilterResult.REJECT;
                        }
                        case ACCEPT_AND_STOP:
                        {
                            result = GraphPathFilterResult.ACCEPT_AND_STOP;
                            break;
                        }
                    }
                }
            }
            return result;
        }
    }
}
