/*
 Copyright (c) 2017 Kevin Jones, All rights reserved.
 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.
 */
package com.nawforce.apexlink.pkg

import com.nawforce.apexlink.names.TypeNames.TypeNameUtils
import com.nawforce.apexlink.org.{OrgImpl, PackageImpl}
import com.nawforce.apexlink.types.apex.{FullDeclaration, SummaryDeclaration}
import com.nawforce.apexlink.{FileSystemHelper, TestHelper}
import com.nawforce.pkgforce.api.{Issue, IssueLocation}
import com.nawforce.pkgforce.names.{Name, TypeName}
import com.nawforce.pkgforce.path.PathLike
import org.scalatest.funsuite.AnyFunSuite

class UnusedTest extends AnyFunSuite with TestHelper {

  implicit class IssueLocationUtils(location: IssueLocation) {
    def displayPosition: String = {
      if (location.startLineNumber() == location.endLineNumber()) {
        if (location.startCharOffset() == 0 && location.endCharOffset() == 0)
          s"line ${location.startLineNumber()}"
        else if (location.startCharOffset() == location.endCharOffset())
          s"line ${location.startLineNumber()} at ${location.startCharOffset()}"
        else
          s"line ${location.startLineNumber()} at ${location.startCharOffset()}-${location.endCharOffset()}"
      } else {
        if (location.startCharOffset() == 0 && location.endCharOffset() == 0)
          s"line ${location.startLineNumber()} to ${location.endLineNumber()}"
        else
          s"line ${location.startLineNumber()}:${location.startCharOffset()} to ${location.endLineNumber()}:${location.endCharOffset()}"
      }
    }
  }

  implicit class IssueUtils(issue: Issue) {
    def asString(): String = {
      s"${issue.category}: ${issue.fileLocation().displayPosition}: ${issue.message}\n"
    }
  }

  def orgIssuesFor(org: OrgImpl, path: PathLike): String = {
    org.issueManager.issuesForFile(path.toString).map(_.asString()).mkString("\n")
  }

  test("Unused method") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {public void foo() {}}",
        "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(
        orgIssuesFor(org, root.join("Dummy.cls"))
          == "Unused: line 1 at 32-35: Unused Method 'void foo()'\n")
    }
  }

  test("Nested unused method") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {public class Inner {public void foo() {}} }",
          "Foo.cls" -> "public class Foo{ {Type t1 = Dummy.class; Type t2 = Dummy.Inner.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(
        orgIssuesFor(org, root.join("Dummy.cls")) ==
          "Unused: line 1 at 52-55: Unused Method 'void foo()'\n")
    }
  }

  test("Method used from same method") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {public void foo() {foo();}}",
          "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
    }
  }

  test("Method used from block") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {{foo();} public void foo() {}}",
          "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
    }
  }

  test("Unused field") {
    FileSystemHelper.run(Map("Dummy.cls" -> "public class Dummy {Object a;}",
                             "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(
        orgIssuesFor(org, root.join("Dummy.cls")) ==
          "Unused: line 1 at 27-28: Unused Field or Property 'a'\n")
    }
  }

  test("Nested unused field") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {public class Inner {Object a;} }",
          "Foo.cls" -> "public class Foo{ {Type t1 = Dummy.class; Type t2 = Dummy.Inner.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(
        orgIssuesFor(org, root.join("Dummy.cls")) ==
          "Unused: line 1 at 47-48: Unused Field or Property 'a'\n")
    }
  }

  test("Field used from method") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {Object a; void foo(){foo(); a = null;}}",
          "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
    }
  }

  test("Field used from block") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {{a = null;} Object a;}",
          "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
    }
  }

  test("Unused class") {
    FileSystemHelper.run(Map("Dummy.cls" -> "public class Dummy {Object a; void func() {}}")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(
        orgIssuesFor(org, root.join("Dummy.cls")) ==
          "Unused: line 1 at 13-18: Type 'Dummy' is unused\n")
    }
  }

  test("Nested unused class") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {public class Inner {Object a; void func() {}} }",
          "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(!org.issues.hasErrorsOrWarnings)
      assert(
        orgIssuesFor(org, root.join("Dummy.cls")) ==
          "Unused: line 1 at 33-38: Type 'Dummy.Inner' is unused\n")
    }
  }

  test("Unused catch") {
    FileSystemHelper.run(
      Map("Dummy.cls" -> "public class Dummy {{ try {} catch(Exception e) {} }}",
        "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
      val org = createHappyOrg(root)
      assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
    }
  }

  def assertIsFullDeclaration(pkg: PackageImpl, name: String, namespace: Option[Name] = None): Unit = {
    assert(
      pkg.orderedModules.head
        .findModuleType(TypeName(Name(name)).withNamespace(namespace))
        .head
        .isInstanceOf[FullDeclaration])
  }

  def assertIsSummaryDeclaration(pkg: PackageImpl, name: String, namespace: Option[Name] = None): Unit = {
    assert(
      pkg.orderedModules.head
        .findModuleType(TypeName(Name(name)).withNamespace(namespace))
        .head
        .isInstanceOf[SummaryDeclaration])
  }

  test("Used method on summary type") {
    withManualFlush {
      FileSystemHelper.run(
        Map("Dummy.cls" -> "public class Dummy {public static void foo() {}}",
            "Caller.cls" -> "public class Caller {{Dummy.Foo();}}",
        )) { root: PathLike =>
        // Setup as cached
        val org = createOrg(root)
        val pkg = org.unmanaged
        assertIsFullDeclaration(pkg, "Dummy")
        assert(!org.issues.hasErrorsOrWarnings)
        assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
        org.flush()

        val org2 = createOrg(root)
        val pkg2 = org2.unmanaged
        assertIsSummaryDeclaration(pkg2, "Dummy")
        OrgImpl.current.withValue(org2) {
          assert(orgIssuesFor(org2, root.join("Dummy.cls")).isEmpty)
        }
      }
    }
  }

  test("Unused method on summary type") {
    withManualFlush {
      FileSystemHelper.run(
        Map("Dummy.cls" -> "public class Dummy {public void foo() {}}",
            "Foo.cls" -> "public class Foo{ {Type t = Dummy.class;} }")) { root: PathLike =>
        // Setup as cached
        val org = createOrg(root)
        val pkg = org.unmanaged
        assertIsFullDeclaration(pkg, "Dummy")
        assert(!org.issues.hasErrorsOrWarnings)
        assert(
          orgIssuesFor(org, root.join("Dummy.cls")) ==
            "Unused: line 1 at 32-35: Unused Method 'void foo()'\n")
        org.flush()

        val org2 = createOrg(root)
        val pkg2 = org2.unmanaged
        assertIsSummaryDeclaration(pkg2, "Dummy")
        OrgImpl.current.withValue(org2) {
          assert(
            orgIssuesFor(org2, root.join("Dummy.cls")) ==
              "Unused: line 1 at 32-35: Unused Method 'void foo()'\n")
        }
      }
    }
  }

  test("Trigger referencing class") {
    FileSystemHelper.run(
      Map("Foo.cls" -> "public class Foo {public static String bar;}",
          "Dummy.trigger" ->
            """trigger Dummy on Account (before insert) {
            |  System.debug(Foo.bar);
            |}""".stripMargin)) { root: PathLike =>
      val org = createOrg(root)
      val pkg = org.unmanaged
      assert(!org.issues.hasErrorsOrWarnings)

      OrgImpl.current.withValue(org) {
        assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
      }
    }
  }

  test("Method referenced from external function call ") {
    FileSystemHelper.run(
      Map(
        "sfdx-project.json" ->
          """{
          |"namespace": "pkg",
          |"packageDirectories": [{"path": "pkg"}],
          |"plugins": {"dependencies": [{"namespace": "ext"}]}
          |}""".stripMargin,
        "pkg/Dummy.cls" -> "public class Dummy {public static void foo() {}}",
        "pkg/Other.cls" -> "public class Other {public void bar() {ext.func(Dummy.foo());}}")) { root: PathLike =>
      val org = createOrg(root)
      val pkg = org.packagesByNamespace(Some(Name("pkg")))
      assert(!org.issues.hasErrorsOrWarnings)
      assert(orgIssuesFor(org, root.join("Dummy.cls")).isEmpty)
    }
  }

  test("Unused local var") {
    FileSystemHelper.run(Map("Dummy.cls" -> "public class Dummy { {Object a;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(
        org.issues.getMessages(root.join("Dummy.cls"), unused = true) ==
          "Unused: line 1 at 13-18: Type 'Dummy' is unused\nUnused: line 1 at 29-30: Unused local variable 'a'\n")
    }
  }

  test("Unused local var assignment") {
    FileSystemHelper.run(Map("Dummy.cls" -> "public class Dummy { {Object a; a=null;} }")) { root: PathLike =>
      val org = createOrg(root)
      assert(org.issues.getMessages(root.join("Dummy.cls"), unused = true) ==
        "Unused: line 1 at 13-18: Type 'Dummy' is unused\n"
      )
    }
  }

  test("Page controller & extension is used") {
    FileSystemHelper.run(
      Map("Test.page" -> "<apex:page controller='Controller' extensions='Extension'/>",
          "Controller.cls" -> "public class Controller { }",
          "Extension.cls" -> "public class Extension { }")) { root: PathLike =>
      val org = createOrg(root)
      val pkg = org.unmanaged
      assert(!org.issues.hasErrorsOrWarnings)

      OrgImpl.current.withValue(org) {
        assert(orgIssuesFor(org, root.join("Controller.cls")).isEmpty)
        assert(orgIssuesFor(org, root.join("Extension.cls")).isEmpty)
      }
    }
  }

  test("Component controller is used") {
    FileSystemHelper.run(
      Map("Test.component" -> "<apex:component controller='Controller'/>",
          "Controller.cls" -> "public class Controller { }")) { root: PathLike =>
      val org = createOrg(root)
      val pkg = org.unmanaged
      assert(!org.issues.hasErrorsOrWarnings)

      OrgImpl.current.withValue(org) {
        assert(orgIssuesFor(org, root.join("Controller.cls")).isEmpty)
      }
    }
  }

  test("Queueable via abstract") {
    FileSystemHelper.run(
      Map("Base.cls" -> "public abstract class Base implements Queueable { {Type t = Dummy.class;} }",
        "Dummy.cls" -> "public class Dummy extends Base {public void execute(QueueableContext context) {} }")) { root: PathLike =>
      val org = createOrg(root)
      val pkg = org.unmanaged
      assert(!org.issues.hasErrorsOrWarnings)

      OrgImpl.current.withValue(org) {
        assert(org.issues.getMessages(root.join("Dummy.cls"), unused = true).isEmpty)
      }
    }
  }
}
