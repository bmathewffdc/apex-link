package io.github.nahforce.apexlink.metadata

import java.io.{File, FileInputStream}

import io.github.nahforce.apexlink.antlr.{ApexLexer, ApexParser}
import io.github.nahforce.apexlink.cst.{CST, CompilationUnit}
import io.github.nahforce.apexlink.utils._
import org.antlr.v4.runtime.CommonTokenStream

case class ApexClass(location: Location, fullName: String, compilationUnit: CompilationUnit) extends Symbol {
  val scopedName : String = fullName
}

object ApexClass {
  def create(fullName: String, path: String): Option[ApexClass] = {

    try {
      val listener = new ThrowingErrorListener
      val fis: FileInputStream = new FileInputStream(new File(path))
      val cis: CaseInsensitiveInputStream = new CaseInsensitiveInputStream(fis)
      val lexer: ApexLexer = new ApexLexer(cis)
      lexer.removeErrorListeners()
      lexer.addErrorListener(listener)

      val tokens: CommonTokenStream = new CommonTokenStream(lexer)
      tokens.fill()

      val parser: ApexParser = new ApexParser(tokens)
      parser.removeErrorListeners()
      parser.setTrace(false)
      parser.addErrorListener(listener)

      val cu = CompilationUnit.construct(parser.compilationUnit())
      Some(new ApexClass(new Location(path, 0), fullName, cu))
    } catch {
      case se: SyntaxException =>
        LinkerLog.logMessage(path, se.line, se.msg)
        None
    }
  }

  private def collect(cst : CST) : List[CST] = {
    try {
      List(cst) ++ cst.children().flatMap(x => collect(x))
    } catch {
      case x : Exception =>
        println("oops"); List()
    }
  }

  def main(args: Array[String]): Unit = {
    val fis: FileInputStream = new FileInputStream(new File(args.head))
    val cis: CaseInsensitiveInputStream = new CaseInsensitiveInputStream(fis)
    cis.dump()
    val lexer: ApexLexer = new ApexLexer(cis)

    val tokens: CommonTokenStream = new CommonTokenStream(lexer)
    tokens.fill()

    val parser: ApexParser = new ApexParser(tokens)
    parser.compilationUnit()
  }
}

