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

package org.finos.legend.pure.m3.serialization.compiler.reference;

import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

/**
 * Exception arising when a valid reference id cannot be resolved. For invalid ids, {@link InvalidReferenceIdException}
 * should be used instead.
 */
public class UnresolvableReferenceIdException extends ReferenceIdResolutionException
{
    public UnresolvableReferenceIdException(String referenceId, String message, Throwable cause)
    {
        super(referenceId, message, cause);
    }

    public UnresolvableReferenceIdException(String referenceId, String message)
    {
        super(referenceId, message);
    }

    public UnresolvableReferenceIdException(String referenceId, Throwable cause)
    {
        this(referenceId, buildMessage(referenceId), cause);
    }

    public UnresolvableReferenceIdException(String referenceId)
    {
        this(referenceId, buildMessage(referenceId));
    }

    private static String buildMessage(String referenceId)
    {
        return (referenceId == null) ?
               "Unresolvable reference id: null" :
               StringEscape.escape(new StringBuilder("Unresolvable reference id: '"), referenceId).append("'").toString();
    }
}