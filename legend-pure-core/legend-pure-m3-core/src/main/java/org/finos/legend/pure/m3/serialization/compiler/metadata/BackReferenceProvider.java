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

import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.primitive.IntObjectMap;

public interface BackReferenceProvider
{
    /**
     * Get the back references for an instance, from all the files that hold them.
     *
     * @param instanceId reference id of the instance
     * @return back references for the instance
     */
    ListIterable<? extends BackReference> getBackReferences(String instanceId);

    /**
     * Get the back references for an instance, grouped by the reference id version of the files that hold them. The
     * ids in a back reference must be resolved with the version of its file.
     *
     * @param instanceId reference id of the instance
     * @return back references for the instance, by reference id version
     */
    IntObjectMap<? extends ListIterable<? extends BackReference>> getBackReferencesByReferenceIdVersion(String instanceId);
}
