/*
 [The "BSD licence"]
 Copyright (c) 2019 Kevin Jones
 All rights reserved.

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

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.nawforce.cst

import com.nawforce.api.Org
import com.nawforce.types.{DependencyDeclaration, TypeDeclaration, TypeName}
import com.nawforce.utils.{DotName, Name}
import scalaz.Memo

import scala.collection.mutable

trait TypeResolver {
  /** Find a type relative to a starting type with a local or global name*/
  def getTypeFor(dotName: DotName, from: TypeDeclaration): Option[TypeDeclaration] = {
    typeCache(dotName, from)
  }

  private val typeCache = Memo.immutableHashMapMemo[(DotName, TypeDeclaration), Option[TypeDeclaration]] {
    case (name: DotName, from: TypeDeclaration) => findTypeFor(name, from)
  }

  private def findTypeFor(dotName: DotName, from: TypeDeclaration): Option[TypeDeclaration] = {
    getNestedType(dotName, from)
      .orElse(getFromSuperType(dotName, from)
        .orElse(getFromOuterType(dotName, from)
          .orElse(
            Org.getType(from.namespace.map(ns => dotName.prepend(ns)).getOrElse(dotName))
              .orElse(Org.getType(dotName))
          )
        )
      )
  }

  private def getNestedType(dotName: DotName, from: TypeDeclaration): Option[TypeDeclaration] = {
    if (dotName.isCompound) {
      None
    } else {
      val matched = from.nestedTypes.filter(_.name == dotName.names.head)
      assert(matched.size < 2)
      matched.headOption
    }
  }

  private def getFromSuperType(dotName: DotName, from: TypeDeclaration): Option[TypeDeclaration] = {
    if (from.superClass.isEmpty)
      return None

    // Avoid recursion of searching for super class in super class, will be found later
    if (dotName == from.superClass.get.asDotName)
      return None

    val superType = getTypeFor(from.superClass.get.asDotName, from)
    if (superType.exists(_.path != from.path)) {
      superType.flatMap(st => getTypeFor(dotName, st))
    } else {
      None
    }
  }

  private def getFromOuterType(dotName: DotName, from: TypeDeclaration): Option[TypeDeclaration] = {
    if (dotName.isCompound || from.outerTypeName.isEmpty) {
      None
    } else {
      val outerType = Org.getType(from.outerTypeName.get.asDotName)
      if (outerType.nonEmpty) {
        if (dotName.names.head == outerType.get.name)
          outerType
        else
          getTypeFor(dotName, outerType.get)
      } else {
        None
      }
    }
  }
}

trait VerifyContext {

  def parent(): Option[VerifyContext]

  def importTypeFor(typeName: TypeName, from: TypeDeclaration): Option[TypeDeclaration]
  def addImport(typeName: TypeName)

  def isVar(name: Name): Boolean
}

class TypeVerifyContext(parentContext: Option[TypeVerifyContext], typeDeclaration: TypeDeclaration)
  extends VerifyContext with TypeResolver {

  private val _imports = mutable.Set[TypeName] ()
  private val _dependencies = mutable.Set[DependencyDeclaration] ()

  def parent(): Option[TypeVerifyContext] = parentContext

  def imports: Set[TypeName] = _imports.diff(TypeName.ApexTypes).toSet

  def depends: Set[ClassBodyDeclaration] = Set()

  def importTypeFor(typeName: TypeName, from: TypeDeclaration): Option[TypeDeclaration] = {
    val td = getTypeFor(typeName.asDotName, from)
    td.foreach(_dependencies += _)
    td
  }

  def addImport(typeName: TypeName): Unit = {
    _imports += typeName
  }

  def isVar(name: Name): Boolean = {
    typeDeclaration.fields.exists(_.name == name) || parentContext.exists(_.isVar(name))
  }
}
