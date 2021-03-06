package org.kframework.unparser

import org.kframework.attributes.{Location, Source}
import org.kframework.builtin.Sorts
import org.kframework.definition._
import org.kframework.kore.{KApply, KToken, KVariable, _}
import org.kframework.parser.{Constant, Term, TermCons}
import org.pcollections.ConsPStack

import collection._
import JavaConverters._

object KOREToTreeNodes {

  import org.kframework.kore.KORE._

  def apply(t: K, mod: Module): Term = t match {
    case t: KToken => Constant(t.s, mod.tokenProductionsFor(Sort(t.sort.name)).head, t.att.getOptional[Location]("Location"), t.att.getOptional[Source]("Source"))
    case a: KApply =>
      val production: Production = mod.productionsFor(KLabel(a.klabel.name)).find(p => p.items.count(_.isInstanceOf[NonTerminal]) == a.klist.size).get
      TermCons(ConsPStack.from((a.klist.items.asScala map { i: K => apply(i, mod) }).reverse asJava),
        production, t.att.getOptional[Location]("Location"), t.att.getOptional[Source]("Source"))
  }

  def up(mod: Module)(t: K): K = t match {
    case v: KVariable => KToken(v.name, Sorts.KVariable, v.att)
    case t: KToken =>
      if (mod.tokenProductionsFor.contains(Sort(t.sort.name))) {
        t
      } else {
        KToken(t.s, Sorts.KString, t.att)
      }
    case s: KSequence =>
      if (s.items.size() == 0)
        KApply(KLabel("#EmptyK"), KList(), s.att)
      else
        upList(mod)(s.items.asScala).reduce((k1, k2) => KApply(KLabel("#KSequence"), KList(k1, k2), s.att))
    case r: KRewrite => KApply(KLabel("#KRewrite"), KList(up(mod)(r.left), up(mod)(r.right)), r.att)
    case t: KApply => KApply(t.klabel, upList(mod)(t.klist.items.asScala), t.att)
  }

  def upList(mod: Module)(items: Seq[K]): Seq[K] = {
    items map up(mod) _
  }

  def toString(t: Term): String = t match {
    case Constant(s, _) => s
    case t@TermCons(items, p) => {
      var i = 0
      val unparsedItems = p.items map {
        case Terminal(s, _) => s
        case NonTerminal(sort) => {
          i = i + 1
          toString(t.get(i - 1))
        }
        case RegexTerminal(_, _, _) => throw new AssertionError("Unimplemented yet")
      }
        
      unparsedItems.mkString(" ")

      //TODO: Recover this code to enable format attribute (in PRETTY output mode).      
      /*if (p.att.contains("format")) {
        p.att.get[String]("format").get.format(unparsedItems: _*)
      } else {
        unparsedItems.mkString(" ")
      }*/
    }
  }
}
