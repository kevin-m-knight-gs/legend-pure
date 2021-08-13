package org.finos.legend.pure.runtime.java.compiled.serialization.model;

public interface ObjOrUpdateVisitor<T>
{
    T visit(Obj obj);
    T visit(ObjUpdate objUpdate);
}
