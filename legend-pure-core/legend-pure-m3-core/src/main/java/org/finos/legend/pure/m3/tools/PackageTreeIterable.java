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

import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.lazy.AbstractLazyIterable;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PackageTreeIterable extends AbstractLazyIterable<Package>
{
    private final ImmutableSet<Package> startingPackages;
    private final boolean depthFirst;

    private PackageTreeIterable(Iterable<? extends Package> startingPackages, boolean depthFirst)
    {
        this.startingPackages = Sets.immutable.withAll(startingPackages);
        this.depthFirst = depthFirst;
    }

    @Override
    public Iterator<Package> iterator()
    {
        return Spliterators.iterator(spliterator());
    }

    @Override
    public Spliterator<Package> spliterator()
    {
        return new PackageTreeSpliterator(this.startingPackages, this.depthFirst);
    }

    @Override
    public void each(Procedure<? super Package> procedure)
    {
        forEach((Consumer<? super Package>) procedure);
    }

    @Override
    public void forEach(Consumer<? super Package> consumer)
    {
        spliterator().forEachRemaining(consumer);
    }

    @Override
    public boolean isEmpty()
    {
        return this.startingPackages.isEmpty();
    }

    @Override
    public Package getAny()
    {
        return this.startingPackages.getAny();
    }

    @Override
    public Package getFirst()
    {
        return this.startingPackages.getFirst();
    }

    @Override
    public Package detect(Predicate<? super Package> predicate)
    {
        return detectOptional(predicate).orElse(null);
    }

    @Override
    public Optional<Package> detectOptional(Predicate<? super Package> predicate)
    {
        return stream().filter(predicate).findFirst();
    }

    @Override
    public boolean anySatisfy(Predicate<? super Package> predicate)
    {
        return stream().anyMatch(predicate);
    }

    @Override
    public boolean allSatisfy(Predicate<? super Package> predicate)
    {
        return stream().anyMatch(predicate);
    }

    @Override
    public boolean noneSatisfy(Predicate<? super Package> predicate)
    {
        return stream().noneMatch(predicate);
    }

    public boolean isDepthFirst()
    {
        return this.depthFirst;
    }

    public Stream<Package> stream()
    {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<Package> parallelStream()
    {
        return StreamSupport.stream(spliterator(), true);
    }

    public static PackageTreeIterable newPackageTreeIterable(Iterable<? extends Package> startingPackages, boolean depthFirst)
    {
        return new PackageTreeIterable(startingPackages, depthFirst);
    }

    public static PackageTreeIterable newPackageTreeIterable(Iterable<? extends Package> startingPackages)
    {
        return newPackageTreeIterable(startingPackages, true);
    }

    public static PackageTreeIterable newPackageTreeIterable(Package startingPackage, boolean depthFirst)
    {
        return newPackageTreeIterable(Sets.immutable.with(startingPackage), depthFirst);
    }

    public static PackageTreeIterable newPackageTreeIterable(Package startingPackage)
    {
        return newPackageTreeIterable(startingPackage, true);
    }

    public static PackageTreeIterable newRootPackageTreeIterable(ModelRepository repository, boolean depthFirst)
    {
        return newPackageTreeIterable((Package) repository.getTopLevel(M3Paths.Root), depthFirst);
    }

    public static PackageTreeIterable newRootPackageTreeIterable(ModelRepository repository)
    {
        return newRootPackageTreeIterable(repository, true);
    }

    public static PackageTreeIterable newRootPackageTreeIterable(ProcessorSupport processorSupport, boolean depthFirst)
    {
        return newPackageTreeIterable((Package) processorSupport.repository_getTopLevel(M3Paths.Root), depthFirst);
    }

    public static PackageTreeIterable newRootPackageTreeIterable(ProcessorSupport processorSupport)
    {
        return newRootPackageTreeIterable(processorSupport, true);
    }

    private static class PackageTreeSpliterator implements Spliterator<Package>
    {
        private final Deque<Package> deque;
        private final boolean depthFirst;

        private PackageTreeSpliterator(Deque<Package> deque, boolean depthFirst)
        {
            this.deque = deque;
            this.depthFirst = depthFirst;
        }

        private PackageTreeSpliterator(ImmutableSet<Package> startingNodes, boolean depthFirst)
        {
            this(new ArrayDeque<>(startingNodes.castToSet()), depthFirst);
        }

        @Override
        public boolean tryAdvance(Consumer<? super Package> action)
        {
            Package pkg = this.deque.pollFirst();
            if (pkg == null)
            {
                return false;
            }

            pkg._children().forEach(this::possiblyAddChild);
            action.accept(pkg);
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super Package> action)
        {
            Package pkg;
            while ((pkg = this.deque.pollFirst()) != null)
            {
                pkg._children().forEach(this::possiblyAddChild);
                action.accept(pkg);
            }
        }

        @Override
        public Spliterator<Package> trySplit()
        {
            if (this.deque.size() < 2)
            {
                return null;
            }

            int splitSize = this.deque.size() / 2;
            Deque<Package> newDeque = new ArrayDeque<>(splitSize);
            for (int i = 0; i < splitSize; i++)
            {
                newDeque.addFirst(this.deque.pollLast());
            }
            return new PackageTreeSpliterator(newDeque, this.depthFirst);
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
            return NONNULL | DISTINCT;
        }

        private void possiblyAddChild(CoreInstance child)
        {
            if (child instanceof Package)
            {
                if (this.depthFirst)
                {
                    this.deque.addFirst((Package) child);
                }
                else
                {
                    this.deque.addLast((Package) child);
                }
            }
        }
    }
}
