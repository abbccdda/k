package org.kframework.kil;

import org.kframework.kil.visitors.Visitor;
import org.w3c.dom.Element;

import aterm.ATermAppl;

/**
 * Used for representing parsing ambiguity. Shouldn't exist after disambiguation.
 */
public class Ambiguity extends Collection {

    public Ambiguity(Element element) {
        super(element);
    }

    public Ambiguity(ATermAppl atm) {
        super(atm);
    }

    public Ambiguity(Ambiguity node) {
        super(node);
    }

    public Ambiguity(String sort, java.util.List<Term> col) {
        super(sort, col);
    }

    @Override
    public String toString() {
        String content = "";

        for (Term term : contents)
            if (term != null)
                content += term + ",";

        if (content.length() > 1)
            content = content.substring(0, content.length() - 1);

        return "amb(" + content + ")";
    }

    @Override
    public Ambiguity shallowCopy() {
        return new Ambiguity(this);
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Bracket)
            return contains(((Bracket) o).getContent());
        if (o instanceof Cast)
            return contains(((Cast) o).getContent());
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i).contains(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.visit(this, p);
    }
}
