package com.wixpress.build.sync

import com.wixpress.build.bazel._
import com.wixpress.build.maven.{DependencyNode, MavenDependencyResolver}
import com.wixpress.build.sync.BazelMavenSynchronizer.{BranchName, PersistMessageHeader}
import org.slf4j.LoggerFactory
import ArtifactoryRemoteStorage._

case class DiffSynchronizer(bazelRepositoryWithManagedDependencies: BazelRepository,
                            targetRepository: BazelRepository, resolver: MavenDependencyResolver,
                            dependenciesRemoteStorage: DependenciesRemoteStorage) {
  private val diffCalculator = DiffCalculator(bazelRepositoryWithManagedDependencies, resolver, dependenciesRemoteStorage)
  private val diffWriter = DiffWriter(targetRepository)

  def sync(localNodes: Set[DependencyNode]) = {
    val updatedLocalNodes = diffCalculator.calculateDivergentDependencies(localNodes)

    diffWriter.persistResolvedDependencies(updatedLocalNodes, localNodes)
  }
}

case class DiffCalculator(bazelRepositoryWithManagedDependencies: BazelRepository,
                     resolver: MavenDependencyResolver,
                     dependenciesRemoteStorage: DependenciesRemoteStorage) {
  private val log = LoggerFactory.getLogger(getClass)

  def calculateDivergentDependencies(localNodes: Set[DependencyNode]): Set[DependencyNode] = {
    val reader = new BazelDependenciesReader(bazelRepositoryWithManagedDependencies.localWorkspace("master"))
    val managedDeps = reader.allDependenciesAsMavenDependencies()

    val managedNodes = resolver.dependencyClosureOf(managedDeps, withManagedDependencies = managedDeps)

    calculateDivergentDependencies(localNodes, managedNodes)
  }

  def calculateDivergentDependencies(localNodes: Set[DependencyNode], managedNodes: Set[DependencyNode]): Set[DependencyNode] = {
    val divergentLocalDependencies = localNodes.forceCompileScopeIfNotProvided diff managedNodes

    decorateNodesWithChecksum(divergentLocalDependencies)
  }

  private def decorateNodesWithChecksum(divergentLocalDependencies: Set[DependencyNode]) = {
    log.info("started fetching sha256 checksums for 3rd party dependencies from artifactory...")
    val nodes = divergentLocalDependencies.map(_.updateChecksumFrom(dependenciesRemoteStorage))
    log.info("completed fetching sha256 checksums.")
    nodes
  }
}

case class DiffWriter(targetRepository: BazelRepository) {
  private val persister = new BazelDependenciesPersister(PersistMessageHeader, BranchName, targetRepository)

  def persistResolvedDependencies(divergentLocalDependencies: Set[DependencyNode], libraryRulesNodes: Set[DependencyNode]): Unit = {
    val localCopy = targetRepository.localWorkspace("master")
    val writer = new BazelDependenciesWriter(localCopy)
    val nodesWithPomPackaging = libraryRulesNodes.filter(_.baseDependency.coordinates.packaging.value == "pom")

    val modifiedFiles = writer.writeDependencies(divergentLocalDependencies, divergentLocalDependencies ++ nodesWithPomPackaging)

    persister.persistWithMessage(modifiedFiles, divergentLocalDependencies.map(_.baseDependency.coordinates))
  }
}