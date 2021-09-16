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

package org.finos.legend.pure.m4.tools;

import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.lazy.AbstractLazyIterable;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public class GraphNodeIterable extends AbstractLazyIterable<CoreInstance>
{
    private final ImmutableList<CoreInstance> startingNodes;

    private GraphNodeIterable(Iterable<? extends CoreInstance> startingNodes)
    {
        this.startingNodes = Lists.immutable.withAll(startingNodes);
    }

    @Override
    public Iterator<CoreInstance> iterator()
    {
        return new GraphNodeIterator(this.startingNodes);
    }

    @Override
    public void each(Procedure<? super CoreInstance> procedure)
    {
        for (CoreInstance node : this)
        {
            procedure.value(node);
        }
    }

    @Override
    public void forEach(Consumer<? super CoreInstance> consumer)
    {
        for (CoreInstance node : this)
        {
            consumer.accept(node);
        }
    }

    public static GraphNodeIterable fromNode(CoreInstance startingNode)
    {
        return fromNodes(Lists.immutable.with(startingNode));
    }

    public static GraphNodeIterable fromNodes(CoreInstance... startingNodes)
    {
        return fromNodes(Lists.immutable.with(startingNodes));
    }

    public static GraphNodeIterable fromNodes(Iterable<? extends CoreInstance> startingNodes)
    {
        return new GraphNodeIterable(Objects.requireNonNull(startingNodes, "Starting nodes may not be null"));
    }

    public static GraphNodeIterable fromModelRepository(ModelRepository repository)
    {
        return fromNodes(repository.getTopLevels());
    }

    public static MutableSet<CoreInstance> allInstancesFromRepository(ModelRepository repository)
    {
        return allConnectedInstances(repository.getTopLevels());
    }

    public static MutableSet<CoreInstance> allConnectedInstances(Iterable<? extends CoreInstance> startingNodes)
    {
        GraphNodeIterator iterator = new GraphNodeIterator(startingNodes);
        while (iterator.hasNext())
        {
            iterator.next();
        }
        return iterator.visited;
    }

    private static class GraphNodeIterator implements Iterator<CoreInstance>
    {
        private final Deque<CoreInstance> deque;
        private final MutableSet<CoreInstance> visited;
        private CoreInstance next = null;

        private GraphNodeIterator(Iterable<? extends CoreInstance> startingNodes)
        {
            this.deque = Iterate.addAllTo(startingNodes, new ArrayDeque<>());
            this.visited = Sets.mutable.ofInitialCapacity(this.deque.size());
            update();
        }

        @Override
        public boolean hasNext()
        {
            return this.next != null;
        }

        @Override
        public CoreInstance next()
        {
            CoreInstance node = this.next;
            if (node == null)
            {
                throw new NoSuchElementException();
            }
            update();
            return node;
        }

        private void update()
        {
            CoreInstance node = getNextUnvisitedFromStack();
            if (node != null)
            {
                node.getKeys().forEach(key -> Iterate.addAllIterable(node.getValueForMetaPropertyToMany(key), this.deque));
            }
            this.next = node;
        }

        private CoreInstance getNextUnvisitedFromStack()
        {
            while (!this.deque.isEmpty())
            {
                CoreInstance node = this.deque.pollFirst();
                if (this.visited.add(node))
                {
                    return node;
                }
            }
            return null;
        }
    }
}
