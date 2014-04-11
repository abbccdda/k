package org.kframework.kil;

import org.kframework.kil.loader.JavaClassesFactory;
import org.kframework.kil.visitors.Visitor;
import org.kframework.utils.xml.XML;
import org.w3c.dom.Element;

import aterm.ATermAppl;

public class BagItem extends CollectionItem {
    public BagItem(String location, String filename) {
        super(location, filename, "BagItem");
    }

    public BagItem(Element element) {
        super(element);
        this.value = (Term) JavaClassesFactory.getTerm(XML.getChildrenElements(element).get(0));
    }

    public BagItem(ATermAppl atm) {
        super(atm);
        value = (Term) JavaClassesFactory.getTerm(atm.getArgument(0));
    }

    public BagItem(BagItem node) {
        super(node);
    }

    public BagItem(Term node) {
        super("BagItem");
        this.value = node;
    }

    public String toString() {
        return this.value.toString();
    }

    public Term getItem() {
        return value;
    }

    public void setItem(Term item) {
        this.value = item;
    }

    @Override
    public BagItem shallowCopy() {
        return new BagItem(this);
    }

    @Override
    public <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.visit(this, p);
    }
}
