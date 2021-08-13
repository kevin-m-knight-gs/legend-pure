package org.finos.legend.pure.runtime.java.compiled.serialization.model;

import java.util.function.Consumer;

public abstract class ObjOrUpdateConsumer implements ObjOrUpdateVisitor<Void>, Consumer<ObjOrUpdate>
{
    @Override
    public Void visit(Obj obj)
    {
        accept(obj);
        return null;
    }

    @Override
    public Void visit(ObjUpdate objUpdate)
    {
        accept(objUpdate);
        return null;
    }

    @Override
    public void accept(ObjOrUpdate objOrUpdate)
    {
        objOrUpdate.visit(this);
    }

    protected abstract void accept(Obj obj);

    protected abstract void accept(ObjUpdate objUpdate);
}
