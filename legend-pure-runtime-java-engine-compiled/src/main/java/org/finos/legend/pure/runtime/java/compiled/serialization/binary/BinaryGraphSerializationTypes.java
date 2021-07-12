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

package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

class BinaryGraphSerializationTypes
{
    // Obj types
    static final byte OBJ = 0;
    static final byte ENUM = 1;
    static final byte OBJ_UPDATE = 2;

    // RValue types
    static final byte OBJ_REF = 3;
    static final byte ENUM_REF = 4;
    static final byte PRIMITIVE_BOOLEAN = 5;
    static final byte PRIMITIVE_DOUBLE = 6;
    static final byte PRIMITIVE_LONG = 7;
    static final byte PRIMITIVE_STRING = 8;
    static final byte PRIMITIVE_DATE = 9;
    static final byte PRIMITIVE_DECIMAL = 10;
}
