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

package org.finos.legend.pure.m3.coreinstance.lazy;

import java.util.function.Supplier;

class SharedSupplier<T> implements Supplier<T>
{
    private volatile Supplier<T> supplier;
    private volatile T value;

    SharedSupplier(Supplier<T> supplier)
    {
        this.supplier = supplier;
    }

    @Override
    public T get()
    {
        if (this.supplier != null)
        {
            synchronized (this)
            {
                Supplier<T> local = this.supplier;
                if (local != null)
                {
                    T result = this.value = local.get();
                    this.supplier = null;
                    return result;
                }
            }
        }
        return this.value;
    }

    boolean isResolved()
    {
        return this.supplier == null;
    }

    T getResolvedValue()
    {
        return this.value;
    }
}
