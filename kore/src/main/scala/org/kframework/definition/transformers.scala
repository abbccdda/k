// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.definition

import java.util.function.BiFunction

import org.kframework.attributes.{Location, Source}
import org.kframework.definition
import org.kframework.kore.K
import org.kframework.utils.errorsystem.KEMException
import collection.JavaConverters._
import collection._

object ModuleTransformer {
  def fromSentenceTransformer(f: java.util.function.UnaryOperator[Sentence], name: String): MemoizingModuleTransformer =
    new BasicModuleTransformer {
      override protected def process(inputModule: Module, alreadyProcessedImports: Set[Module]): Module = {
        Module(inputModule.name, alreadyProcessedImports, inputModule.localSentences.map(s => try {
          f(s)
        } catch {
          case e: KEMException =>
            if (e.exception.getLocation == null)
              throw new KEMException(e.exception.copy(s.att.get(classOf[Location]).orNull))
            else
              throw e
        }
        ))
      }
    }

  def fromSentenceTransformer(f: (Module, Sentence) => Sentence, passName: String): HybridMemoizingModuleTransformer =
    new HybridMemoizingModuleTransformer {
      override def processHybridModule(m: Module): Module = {
        val newSentences = m.localSentences map { s =>
          try {
            f(m, s)
          } catch {
            case e: KEMException =>
              e.exception.addTraceFrame("while executing phase \"" + name + "\" on sentence at"
                + "\n\t" + s.att.get(classOf[Source]).map(_.toString).getOrElse("<none>")
                + "\n\t" + s.att.get(classOf[Location]).map(_.toString).getOrElse("<none>"))
              throw e
          }
        }
        if (newSentences != m.localSentences)
          Module(m.name, m.imports, newSentences, m.att)
        else
          m
      }
      override val name: String = passName
    }

  def fromRuleBodyTranformer(f: K => K, name: String): MemoizingModuleTransformer =
    fromSentenceTransformer(_ match { case r: Rule => r.copy(body = f(r.body)); case s => s }, name)

  def fromKTransformerWithModuleInfo(ff: Module => K => K, name: String): HybridMemoizingModuleTransformer =
    fromSentenceTransformer((module, sentence) => {
      val f: K => K = ff(module)
      sentence match {
        case r: Rule => Rule.apply(f(r.body), f(r.requires), f(r.ensures), r.att)
        case c: Context => Context.apply(f(c.body), f(c.requires), c.att)
        case o => o
      }
    }, name)

  def fromKTransformer(f: K => K, name: String): HybridMemoizingModuleTransformer =
    fromKTransformerWithModuleInfo((m: Module) => f, name)

  def fromHybrid(f: Module => Module, name: String): HybridMemoizingModuleTransformer = {
    val lName = name
    new HybridMemoizingModuleTransformer {
      override def processHybridModule(hybridModule: Module): Module = f(hybridModule)
      override val name: String = lName
    }
  }
}

class ModuleTransformerException(moduleTransformerName: String, cause: Throwable) extends Exception(cause) {
  override def toString = moduleTransformerName + " : " + cause.toString
}

/**
  * Any overriding class should call wrapExceptions to make sure its
  * errors are correctly wrapped in a ModuleTransformerException
  */
abstract class ModuleTransformer extends (Module => Module) {
  val name: String = this.getClass.getName
  def wrapExceptions(f: => Module): Module = try {
    f
  } catch {
    case e: ModuleTransformerException => throw e
    case e: Throwable => throw new ModuleTransformerException(name, e)
  }
}

/**
  * A module transformer with memoization
  */
abstract class MemoizingModuleTransformer extends ModuleTransformer {
  val memoization = collection.concurrent.TrieMap[Module, Module]()
  val currentProcessedModules = new java.util.concurrent.LinkedBlockingDeque[Module]()

  override def apply(input: Module): Module = {
    if (currentProcessedModules.contains(input))
      throw new AssertionError("Found a cycle on: " + input.name + " with chain: " + currentProcessedModules.asScala.map(_.name).toList.reverse.mkString(" -> "))
    currentProcessedModules.push(input)
    val res = wrapExceptions(memoization.getOrElseUpdate(input, {processModule(input)}))
    currentProcessedModules.pop()
    res
  }

  protected def processModule(inputModule: Module): Module
}

abstract class AbstractMemoizingModuleTransformer extends MemoizingModuleTransformer

/**
  * Marker trait for a ModuleTransformer having access to the entire original definition
  */
trait WithInputDefinition {
  val inputDefinition: Definition
}

/**
  * The processHybridModule function take a module with all the imported modules already transformed,
  * and uses it to create the updated module.
  *
  * Natural to use but a bit risky as the "hybrid" module may not be consistent.
  */
trait HybridModuleTransformer extends ModuleTransformer {
  def apply(input: Module): Module = wrapExceptions({
    val newImports = input.imports map this
    if (newImports != input.imports)
      processHybridModule(Module(input.name, newImports, input.localSentences, input.att))
    else
      processHybridModule(input)
  })

  protected def processHybridModule(hybridModule: Module): Module
}

abstract class BasicModuleTransformer extends MemoizingModuleTransformer {
  def processModule(input: Module): Module = wrapExceptions({
    process(input, input.imports map this)
  })

  protected def process(inputModule: Module, alreadyProcessedImports: Set[Module]): Module
}

abstract class WithInputDefinitionModuleTransformer(inputDefinition: Definition) extends BasicModuleTransformer {
  def apply(moduleName: String): Module = this (inputDefinition.getModule(moduleName).get)
  def outputDefinition = new DefinitionTransformer(this).apply(inputDefinition)
}


abstract class HybridMemoizingModuleTransformer extends MemoizingModuleTransformer with HybridModuleTransformer {
  override def apply(input: Module): Module = super[MemoizingModuleTransformer].apply(input)

  protected def processModule(inputModule: Module): Module = super[HybridModuleTransformer].apply(inputModule)
}

object DefinitionTransformer {
  def fromSentenceTransformer(f: Sentence => Sentence, name: String): DefinitionTransformer =
    new DefinitionTransformer(ModuleTransformer.fromSentenceTransformer(f(_), name))

  def fromSentenceTransformer(f: (Module, Sentence) => Sentence, name: String): DefinitionTransformer =
    DefinitionTransformer(ModuleTransformer.fromSentenceTransformer(f, name))

  def fromRuleBodyTranformer(f: K => K, name: String): DefinitionTransformer =
    new DefinitionTransformer(ModuleTransformer.fromRuleBodyTranformer(f, name))

  def fromKTransformer(f: K => K, name: String): DefinitionTransformer =
    DefinitionTransformer(ModuleTransformer.fromKTransformer(f, name))

  def fromKTransformerWithModuleInfo(f: (Module, K) => K, name: String): DefinitionTransformer =
    DefinitionTransformer(ModuleTransformer.fromKTransformerWithModuleInfo(f.curried, name))

  def fromHybrid(f: Module => Module, name: String): DefinitionTransformer = DefinitionTransformer(ModuleTransformer.fromHybrid(f, name))

  def apply(f: HybridMemoizingModuleTransformer): DefinitionTransformer = new DefinitionTransformer(f)

  def fromWithInputDefinitionTransformerClass(c: Class[_]): (Definition => Definition) = (d: Definition) => c.getConstructor(classOf[Definition]).newInstance(d).asInstanceOf[WithInputDefinitionModuleTransformer].outputDefinition

}

class DefinitionTransformer(moduleTransformer: MemoizingModuleTransformer) extends (Definition => Definition) {
  override def apply(d: Definition): Definition = {
    //    definition.Definition(
    //      moduleTransformer(d.mainModule),
    //      d.entryModules map moduleTransformer,
    //      d.att)
    // commented above such that the regular transformer behaves like the SelectiveDefinitionTransformer
    // this avoids a bug in the configuration concretization functionality
    new SelectiveDefinitionTransformer(moduleTransformer).apply(d)
  }
}

/**
  * Only transforms modules which are reachable from mainModule or mainSyntaxModule
  */
class SelectiveDefinitionTransformer(moduleTransformer: MemoizingModuleTransformer) extends (Definition => Definition) {
  override def apply(d: Definition): Definition = {
    // TODO: Cosmin: the two lines below are a hack to make sure the modules are processed by the pass regardless of
    // them not being reachable from the main module
    // I think the right fix would be to explicitly import them when needed
    List("STDIN-STREAM", "STDOUT-STREAM", "BASIC-K", "K", "RULE-PARSER", "CONFIG-CELLS",
      "PROGRAM-LISTS", "K-TERM", "ID-PROGRAM-PARSING", "LANGUAGE-PARSING", "MAP", "DEFAULT-CONFIGURATION",
      "K-REFLECTION")
      .foreach(d.getModule(_).foreach(moduleTransformer))

    d.entryModules
      .filter(m => m.name.endsWith("-PROGRAM-PARSING") || m.name.endsWith("-SYNTAX"))
      .foreach(moduleTransformer)

    val newMainModule = moduleTransformer(d.mainModule)
    val newEntryModules = d.entryModules flatMap moduleTransformer.memoization.get
    val newEntryModuleNames = newEntryModules.map(_.name)

    definition.Definition(
      newMainModule,
      newEntryModules, // the trick is that any memoized modules have already been transformed
      d.att)
  }
}
