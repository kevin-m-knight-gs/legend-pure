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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;

class ExternalReferenceIndex
{
    private final MapIterable<String, ? extends MapIterable<String, ExternalReference>> index;

    private ExternalReferenceIndex(MapIterable<String, ? extends MapIterable<String, ExternalReference>> index)
    {
        this.index = index;
    }

    boolean hasReferenceTo(String owner, String reference)
    {
        MapIterable<String, ExternalReference> map = this.index.get(reference);
        return (map != null) && map.containsKey(owner);
    }

    boolean hasAnyReferenceTo(String reference)
    {
        return this.index.containsKey(reference);
    }

    RichIterable<String> getReferencingElements(String reference)
    {
        MapIterable<String, ExternalReference> map = this.index.get(reference);
        return (map == null) ? Lists.immutable.empty() : map.keysView();
    }

    RichIterable<BackReference> getBackReferences(String reference)
    {
        MapIterable<String, ExternalReference> map = this.index.get(reference);
        return (map == null) ? Lists.immutable.empty() : map.valuesView().flatCollect(ExternalReference::getBackReferences);
    }

    static ExternalReferenceIndex buildIndex(ElementIndex elementIndex)
    {
        MutableMap<String, MutableMap<String, ExternalReference>> index = Maps.mutable.empty();
        elementIndex.forEachElement(e -> e.getExternalReferences().forEach(ref -> index.getIfAbsentPut(ref.getReferenceId(), Maps.mutable::empty).put(e.getPath(), ref)));
        return new ExternalReferenceIndex(index);
    }
}
