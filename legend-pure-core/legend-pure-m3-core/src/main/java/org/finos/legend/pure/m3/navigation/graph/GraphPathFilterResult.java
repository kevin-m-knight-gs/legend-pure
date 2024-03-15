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

public enum GraphPathFilterResult
{
    ACCEPT_AND_CONTINUE(true, true),
    ACCEPT_AND_STOP(true, false),
    REJECT(false, false);

    private final boolean shouldAccept;
    private final boolean shouldContinue;

    GraphPathFilterResult(boolean shouldAccept, boolean shouldContinue)
    {
        this.shouldAccept = shouldAccept;
        this.shouldContinue = shouldContinue;
    }

    public boolean shouldAccept()
    {
        return this.shouldAccept;
    }

    public boolean shouldContinue()
    {
        return this.shouldContinue;
    }
}
