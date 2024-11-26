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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;

import java.util.function.BiConsumer;

class ExternalReferenceIndex
{
    private final MapIterable<String, ImmutableList<String>> index;

    private ExternalReferenceIndex(MapIterable<String, ImmutableList<String>> index)
    {
        this.index = index;
    }

    boolean hasReferencesTo(String referenceId)
    {
        return this.index.containsKey(referenceId);
    }

    RichIterable<String> getAllExternalReferenceIds()
    {
        return this.index.keysView();
    }

    ImmutableList<String> getAllReferencesTo(String referenceId)
    {
        ImmutableList<String> refs = this.index.get(referenceId);
        return (refs == null) ? Lists.immutable.empty() : refs;
    }

    void forEachExternalReference(BiConsumer<? super String, ? super String> consumer)
    {
        this.index.forEachKeyValue((ref, owners) -> owners.forEach(owner -> consumer.accept(ref, owner)));
    }

    void forEachIdWithReferencesTo(BiConsumer<? super String, ? super ImmutableList<String>> consumer)
    {
        this.index.forEachKeyValue(consumer::accept);
    }

    static ExternalReferenceIndex buildIndex(ElementIndex elementIndex)
    {
        MutableMap<String, MutableList<String>> initialIndex = Maps.mutable.empty();
        elementIndex.forEachElement(e -> e.getExternalReferences().forEach(refId -> initialIndex.getIfAbsentPut(refId, Lists.mutable::empty).add(e.getPath())));

        MutableMap<String, ImmutableList<String>> index = Maps.mutable.ofInitialCapacity(initialIndex.size());
        initialIndex.forEachKeyValue((refId, refOwners) -> index.put(refId, refOwners.sortThis().toImmutable()));
        return new ExternalReferenceIndex(index);
    }
}
