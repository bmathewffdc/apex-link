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
package com.nawforce.types

import com.nawforce.api._
import com.nawforce.documents.Location
import com.nawforce.utils.{DotName, Name}

import scala.collection.mutable

sealed trait Nature {def value: String}
case object CLASS_NATURE extends Nature {override def value: String = "class"}
case object INTERFACE_NATURE extends Nature {override def value: String = "interface"}
case object ENUM_NATURE extends Nature {override def value: String = "enum"}

trait Dependant {
  private val dependencyHolders = mutable.Set[DependencyHolder]()

  def addDependencyHolder(dependencyHolder: DependencyHolder): Unit = {
    dependencyHolders.add(dependencyHolder)
  }

  def hasHolders: Boolean = dependencyHolders.nonEmpty

  def getDependencyHolders: Set[DependencyHolder] = dependencyHolders.toSet
}

trait DependencyHolder extends Dependant {
  def dependencies(): Set[Dependant] = Set.empty
  def propagateDependencies(): Unit = {
    dependencies().foreach(_.addDependencyHolder(this))
  }
}

trait BlockDeclaration extends DependencyHolder {
  val isStatic: Boolean
}

trait FieldDeclaration extends DependencyHolder {
  val name: Name
  val modifiers: Seq[Modifier]
  val typeName: TypeName
  val readAccess: Modifier
  val writeAccess: Modifier

  lazy val isStatic: Boolean = modifiers.contains(STATIC_MODIFIER)

  lazy val summary: FieldSummary = FieldSummary(name.toString, modifiers.map(_.toString).sorted.toList,
    typeName.toString, readAccess.toString, writeAccess.toString)
}

trait ParameterDeclaration {
  val name: Name
  val typeName: TypeName

  lazy val summary: ParameterSummary = ParameterSummary(name.toString, typeName.toString)
}

trait ConstructorDeclaration extends DependencyHolder {
  val modifiers: Seq[Modifier]
  val parameters: Seq[ParameterDeclaration]

  lazy val summary: ConstructorSummary = ConstructorSummary(
    modifiers.map(_.toString).sorted.toList,
    parameters.map(_.summary).sortBy(_.name).toList
  )
}

trait MethodDeclaration extends DependencyHolder {
  val name: Name
  val modifiers: Seq[Modifier]
  val typeName: TypeName
  val parameters: Seq[ParameterDeclaration]

  lazy val summary: MethodSummary = MethodSummary(
    name.toString, modifiers.map(_.toString).sorted.toList, typeName.toString,
    parameters.map(_.summary).sortBy(_.name).toList
  )
}

trait TypeDeclaration extends DependencyHolder {
  val name: Name
  val typeName: TypeName
  val outerTypeName: Option[TypeName]
  val nature: Nature
  val modifiers: Seq[Modifier]
  val isComplete: Boolean
  val isExternallyVisible: Boolean

  lazy val namespace: Option[Name] = {
    val outermostType = outerTypeName.getOrElse(typeName).outer
    assert(outermostType.forall(_.outer.isEmpty))
    outermostType.map(_.name)
  }

  val superClass: Option[TypeName]
  def superClassDeclaration: Option[TypeDeclaration]
  val interfaces: Seq[TypeName]
  def nestedTypes: Seq[TypeDeclaration]

  val blocks: Seq[BlockDeclaration]
  val fields: Seq[FieldDeclaration]
  val constructors: Seq[ConstructorDeclaration]
  val methods: Seq[MethodDeclaration]

  /** Validate must be called before examining dependencies */
  def validate(): Unit
  def collectDependencies(dependencies: mutable.Set[Dependant]): Unit

  // TODO Remove?
  def validateReference(location: Location, dotName: DotName): Option[Dependant] = None

  def findField(name: Name, staticOnly: Boolean): Option[FieldDeclaration] = {
    fieldsByName.get(name).filter(f => !staticOnly || f.isStatic)
  }

  private lazy val fieldsByName: Map[Name, FieldDeclaration] = {
    // TODO: Should not be accessing Org here
    val outerType = outerTypeName.flatMap(typeName => Org.getType(namespace, typeName.asDotName))
    val fieldsByName =
        outerType.map(td => td.fields.filter(_.isStatic).map(f => (f.name, f))).getOrElse(Seq()) ++
        fields.map(f => (f.name, f))
    fieldsByName.toMap
  }

  lazy val summary: TypeSummary = TypeSummary(
    name.toString, typeName.toString, nature.value, modifiers.map(_.toString).sorted.toList,
    superClass.map(_.toString).getOrElse(""), interfaces.map(_.toString).sorted.toList,
    fields.map(_.summary).sortBy(_.name).toList,
    constructors.map(_.summary).sortBy(_.parameters.size).toList,
    methods.map(_.summary).sortBy(_.name).toList,
    nestedTypes.map(_.summary).sortBy(_.name).toList
  )
}
