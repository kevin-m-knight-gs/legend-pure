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

package org.finos.legend.pure.m3.serialization.compiler.strings.v2;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.junit.Assert;
import org.junit.Test;

public class TestSpecialStrings
{
    @Test
    public void testSpecialStringIds()
    {
        Assert.assertEquals(-128, BaseStringIndex.getSpecialStringId(null));
        Assert.assertEquals(-127, BaseStringIndex.getSpecialStringId(""));
        Assert.assertEquals(-126, BaseStringIndex.getSpecialStringId("::"));
        Assert.assertEquals(-125, BaseStringIndex.getSpecialStringId("/"));
        Assert.assertEquals(-124, BaseStringIndex.getSpecialStringId("."));
        Assert.assertEquals(-123, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::relationship::Association"));
        Assert.assertEquals(-122, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::Class"));
        Assert.assertEquals(-121, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::ConcreteFunctionDefinition"));
        Assert.assertEquals(-120, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::Enumeration"));
        Assert.assertEquals(-119, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::NativeFunction"));
        Assert.assertEquals(-118, BaseStringIndex.getSpecialStringId("Package"));
        Assert.assertEquals(-117, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::PrimitiveType"));
        Assert.assertEquals(-116, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::extension::Profile"));
        Assert.assertEquals(-115, BaseStringIndex.getSpecialStringId("Root"));
        Assert.assertEquals(-114, BaseStringIndex.getSpecialStringId("function"));
        Assert.assertEquals(-113, BaseStringIndex.getSpecialStringId("meta"));
        Assert.assertEquals(-112, BaseStringIndex.getSpecialStringId("metamodel"));
        Assert.assertEquals(-111, BaseStringIndex.getSpecialStringId("property"));
        Assert.assertEquals(-110, BaseStringIndex.getSpecialStringId("pure"));
        Assert.assertEquals(-109, BaseStringIndex.getSpecialStringId("relationship"));
        Assert.assertEquals(-108, BaseStringIndex.getSpecialStringId("type"));
        Assert.assertEquals(-107, BaseStringIndex.getSpecialStringId("children"));
        Assert.assertEquals(-106, BaseStringIndex.getSpecialStringId("classifierGenericType"));
        Assert.assertEquals(-105, BaseStringIndex.getSpecialStringId("constraints"));
        Assert.assertEquals(-104, BaseStringIndex.getSpecialStringId("expressionSequence"));
        Assert.assertEquals(-103, BaseStringIndex.getSpecialStringId("func"));
        Assert.assertEquals(-102, BaseStringIndex.getSpecialStringId("genericType"));
        Assert.assertEquals(-101, BaseStringIndex.getSpecialStringId("multiplicity"));
        Assert.assertEquals(-100, BaseStringIndex.getSpecialStringId("multiplicityArguments"));
        Assert.assertEquals(-99, BaseStringIndex.getSpecialStringId("multiplicityParameters"));
        Assert.assertEquals(-98, BaseStringIndex.getSpecialStringId("owner"));
        Assert.assertEquals(-97, BaseStringIndex.getSpecialStringId("postConstraints"));
        Assert.assertEquals(-96, BaseStringIndex.getSpecialStringId("preConstraints"));
        Assert.assertEquals(-95, BaseStringIndex.getSpecialStringId("properties"));
        Assert.assertEquals(-94, BaseStringIndex.getSpecialStringId("propertiesFromAssociations"));
        Assert.assertEquals(-93, BaseStringIndex.getSpecialStringId("qualifiedProperties"));
        Assert.assertEquals(-92, BaseStringIndex.getSpecialStringId("qualifiedPropertiesFromAssociations"));
        Assert.assertEquals(-91, BaseStringIndex.getSpecialStringId("rawType"));
        Assert.assertEquals(-90, BaseStringIndex.getSpecialStringId("resolvedEnum"));
        Assert.assertEquals(-89, BaseStringIndex.getSpecialStringId("resolvedNode"));
        Assert.assertEquals(-88, BaseStringIndex.getSpecialStringId("resolvedProperty"));
        Assert.assertEquals(-87, BaseStringIndex.getSpecialStringId("returnMultiplicity"));
        Assert.assertEquals(-86, BaseStringIndex.getSpecialStringId("returnType"));
        Assert.assertEquals(-85, BaseStringIndex.getSpecialStringId("stereotypes"));
        Assert.assertEquals(-84, BaseStringIndex.getSpecialStringId("tag"));
        Assert.assertEquals(-83, BaseStringIndex.getSpecialStringId("taggedValues"));
        Assert.assertEquals(-82, BaseStringIndex.getSpecialStringId("typeArguments"));
        Assert.assertEquals(-81, BaseStringIndex.getSpecialStringId("typeParameters"));
        Assert.assertEquals(-80, BaseStringIndex.getSpecialStringId("values"));

        Assert.assertEquals(0, BaseStringIndex.getSpecialStringId("not a special string"));
        Assert.assertEquals(0, BaseStringIndex.getSpecialStringId("the quick brown fox"));
        Assert.assertEquals(0, BaseStringIndex.getSpecialStringId("jumped over the lazy dog"));
    }

    @Test
    public void testSpecialStringsById()
    {
        Assert.assertNull(BaseStringIndex.getSpecialString(-128));
        Assert.assertEquals("", BaseStringIndex.getSpecialString(-127));
        Assert.assertEquals("::", BaseStringIndex.getSpecialString(-126));
        Assert.assertEquals("/", BaseStringIndex.getSpecialString(-125));
        Assert.assertEquals(".", BaseStringIndex.getSpecialString(-124));
        Assert.assertEquals("meta::pure::metamodel::relationship::Association", BaseStringIndex.getSpecialString(-123));
        Assert.assertEquals("meta::pure::metamodel::type::Class", BaseStringIndex.getSpecialString(-122));
        Assert.assertEquals("meta::pure::metamodel::function::ConcreteFunctionDefinition", BaseStringIndex.getSpecialString(-121));
        Assert.assertEquals("meta::pure::metamodel::type::Enumeration", BaseStringIndex.getSpecialString(-120));
        Assert.assertEquals("meta::pure::metamodel::function::NativeFunction", BaseStringIndex.getSpecialString(-119));
        Assert.assertEquals("Package", BaseStringIndex.getSpecialString(-118));
        Assert.assertEquals("meta::pure::metamodel::type::PrimitiveType", BaseStringIndex.getSpecialString(-117));
        Assert.assertEquals("meta::pure::metamodel::extension::Profile", BaseStringIndex.getSpecialString(-116));
        Assert.assertEquals("Root", BaseStringIndex.getSpecialString(-115));
        Assert.assertEquals("function", BaseStringIndex.getSpecialString(-114));
        Assert.assertEquals("meta", BaseStringIndex.getSpecialString(-113));
        Assert.assertEquals("metamodel", BaseStringIndex.getSpecialString(-112));
        Assert.assertEquals("property", BaseStringIndex.getSpecialString(-111));
        Assert.assertEquals("pure", BaseStringIndex.getSpecialString(-110));
        Assert.assertEquals("relationship", BaseStringIndex.getSpecialString(-109));
        Assert.assertEquals("type", BaseStringIndex.getSpecialString(-108));
        Assert.assertEquals("children", BaseStringIndex.getSpecialString(-107));
        Assert.assertEquals("classifierGenericType", BaseStringIndex.getSpecialString(-106));
        Assert.assertEquals("constraints", BaseStringIndex.getSpecialString(-105));
        Assert.assertEquals("expressionSequence", BaseStringIndex.getSpecialString(-104));
        Assert.assertEquals("func", BaseStringIndex.getSpecialString(-103));
        Assert.assertEquals("genericType", BaseStringIndex.getSpecialString(-102));
        Assert.assertEquals("multiplicity", BaseStringIndex.getSpecialString(-101));
        Assert.assertEquals("multiplicityArguments", BaseStringIndex.getSpecialString(-100));
        Assert.assertEquals("multiplicityParameters", BaseStringIndex.getSpecialString(-99));
        Assert.assertEquals("owner", BaseStringIndex.getSpecialString(-98));
        Assert.assertEquals("postConstraints", BaseStringIndex.getSpecialString(-97));
        Assert.assertEquals("preConstraints", BaseStringIndex.getSpecialString(-96));
        Assert.assertEquals("properties", BaseStringIndex.getSpecialString(-95));
        Assert.assertEquals("propertiesFromAssociations", BaseStringIndex.getSpecialString(-94));
        Assert.assertEquals("qualifiedProperties", BaseStringIndex.getSpecialString(-93));
        Assert.assertEquals("qualifiedPropertiesFromAssociations", BaseStringIndex.getSpecialString(-92));
        Assert.assertEquals("rawType", BaseStringIndex.getSpecialString(-91));
        Assert.assertEquals("resolvedEnum", BaseStringIndex.getSpecialString(-90));
        Assert.assertEquals("resolvedNode", BaseStringIndex.getSpecialString(-89));
        Assert.assertEquals("resolvedProperty", BaseStringIndex.getSpecialString(-88));
        Assert.assertEquals("returnMultiplicity", BaseStringIndex.getSpecialString(-87));
        Assert.assertEquals("returnType", BaseStringIndex.getSpecialString(-86));
        Assert.assertEquals("stereotypes", BaseStringIndex.getSpecialString(-85));
        Assert.assertEquals("tag", BaseStringIndex.getSpecialString(-84));
        Assert.assertEquals("taggedValues", BaseStringIndex.getSpecialString(-83));
        Assert.assertEquals("typeArguments", BaseStringIndex.getSpecialString(-82));
        Assert.assertEquals("typeParameters", BaseStringIndex.getSpecialString(-81));
        Assert.assertEquals("values", BaseStringIndex.getSpecialString(-80));

        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(0));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(1));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(10));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-50));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-51));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-129));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-130));
    }

    @Test
    public void testIsSpecialStringId()
    {
        for (int i = -128; i <= -80; i++)
        {
            Assert.assertTrue(Integer.toString(i), BaseStringIndex.isSpecialStringId(i));
        }

        Assert.assertFalse(BaseStringIndex.isSpecialStringId(0));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(1));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-50));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-51));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-129));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-130));
    }

    @Test
    public void testForEachSpecialString()
    {
        MutableList<String> expected = Lists.mutable.with(
                null,
                "",
                "::",
                "/",
                ".",
                "meta::pure::metamodel::relationship::Association",
                "meta::pure::metamodel::type::Class",
                "meta::pure::metamodel::function::ConcreteFunctionDefinition",
                "meta::pure::metamodel::type::Enumeration",
                "meta::pure::metamodel::function::NativeFunction",
                "Package",
                "meta::pure::metamodel::type::PrimitiveType",
                "meta::pure::metamodel::extension::Profile",
                "Root",
                "function",
                "meta",
                "metamodel",
                "property",
                "pure",
                "relationship",
                "type",
                "children",
                "classifierGenericType",
                "constraints",
                "expressionSequence",
                "func",
                "genericType",
                "multiplicity",
                "multiplicityArguments",
                "multiplicityParameters",
                "owner",
                "postConstraints",
                "preConstraints",
                "properties",
                "propertiesFromAssociations",
                "qualifiedProperties",
                "qualifiedPropertiesFromAssociations",
                "rawType",
                "resolvedEnum",
                "resolvedNode",
                "resolvedProperty",
                "returnMultiplicity",
                "returnType",
                "stereotypes",
                "tag",
                "taggedValues",
                "typeArguments",
                "typeParameters",
                "values");

        MutableList<String> actual = Lists.mutable.empty();
        BaseStringIndex.forEachSpecialString(actual::add);
        Assert.assertEquals(expected, actual);
        Assert.assertTrue(actual.size() <= Byte.MAX_VALUE);
    }
}
