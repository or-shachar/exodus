package com.wixpress.build.sync

import com.wix.bazel.migrator.model.SourceModule
import com.wixpress.build.bazel.{BazelDependenciesReader, BazelRepository}
import com.wixpress.build.maven._
import org.slf4j.LoggerFactory

class UserAddedDepsDiffSynchronizer(bazelRepo: BazelRepository, bazelRepoWithManagedDependencies: BazelRepository,
                                    ManagedDependenciesArtifact: Coordinates, aetherResolver: MavenDependencyResolver,
                                    remoteStorage: DependenciesRemoteStorage,
                                    mavenModules: Set[SourceModule],
                                    randomString: => String) {
  val diffWriter = DiffWriter(bazelRepo, Some(s"user_added_3rd_party_deps_$randomString"))
  val diffCalculator = DiffCalculator(bazelRepoWithManagedDependencies, aetherResolver, remoteStorage)

  private val log = LoggerFactory.getLogger(getClass)

  def syncThirdParties(userAddedDependencies: Set[Dependency]): DiffResult = {
    val diffResult = resolveUpdatedLocalNodes(userAddedDependencies)

    log.info(s"writes updates for ${diffResult.updatedLocalNodes.size} dependency nodes...")
    diffWriter.persistResolvedDependencies(diffResult.updatedLocalNodes, diffResult.localNodes)
    log.info(s"Finished writing updates.")

    diffResult
  }

  def resolveUpdatedLocalNodes(userAddedDependencies: Set[Dependency]): DiffResult = {
    val managedNodes = readManagedNodes()
    val localNodes = readLocalDependencyNodes(externalDependencyNodes = managedNodes)
    val addedNodes = resolveUserAddedDependencyNodes(userAddedDependencies)
    val aggregateNodes = aggregateLocalAndUserAddedNodes(localNodes = localNodes, userAddedDependencies, addedNodes = addedNodes)
    val divergentLocalNodes = calculateDivergentLocalNodes(managedNodes, aggregateNodes)

    DiffResult(divergentLocalNodes, localNodes, managedNodes)
  }

  private def readManagedNodes() = {
    log.info(s"read managed dependencies from external repo Bazel files...")

    val managedDepsRepoReader = new BazelDependenciesReader(bazelRepoWithManagedDependencies.localWorkspace("master"))
    managedDepsRepoReader.allDependenciesAsMavenDependencyNodes()
  }

  private def readLocalDependencyNodes(externalDependencyNodes: Set[DependencyNode]) = {
    log.info("read local dependencies from Bazel files...")

    val localRepoReader = new BazelDependenciesReader(bazelRepo.localWorkspace(branchName = "master"))
    localRepoReader.allDependenciesAsMavenDependencyNodes(externalDependencyNodes.map(_.baseDependency))
  }

  private def resolveUserAddedDependencyNodes(userAddedDependencies: Set[Dependency]) = {
    log.info("obtain managed Dependencies From Maven for userAddedDependencies closure calculation...")
    val managedDependenciesFromMaven = aetherResolver
      .managedDependenciesOf(ManagedDependenciesArtifact)
      .forceCompileScope

    log.info("resolve userAddedDependencies full closure...")
    aetherResolver.dependencyClosureOf(userAddedDependencies, managedDependenciesFromMaven)
  }

  private def calculateDivergentLocalNodes(managedNodes: Set[DependencyNode], aggregateNodes: Set[DependencyNode]) = {
    log.info("calculate diff with managed deps and persist it...")
    log.debug(s"aggregateNodes count: ${aggregateNodes.size}")
    diffCalculator.calculateDivergentDependencies(aggregateNodes, managedNodes)
  }

  def aggregateLocalAndUserAddedNodes(localNodes: Set[DependencyNode], addedDeps: Set[Dependency], addedNodes: Set[DependencyNode]): Set[DependencyNode] = {
    log.info("combine userAddedDependencies with local dependencies...")

    val externalAddedNodes = filterNotLocalSourceModules(addedNodes)
    val newAddedNodesThatAreNotExcludedLocally = newNodesWithLocalExclusionsFilteredOut(externalAddedNodes, localNodes)
    val updatedLocalNodes = updateLocalNodesWithAddedDepsVersions(externalAddedNodes, localNodes, addedDeps)

    updatedLocalNodes ++ newAddedNodesThatAreNotExcludedLocally
  }

  private def filterNotLocalSourceModules(addedNodes: Set[DependencyNode]) = {
    new GlobalExclusionFilterer(mavenModules.map(_.coordinates)).filterGlobalsFromDependencyNodes(addedNodes)
  }

  private def newNodesWithLocalExclusionsFilteredOut(externalAddedNodes: Set[DependencyNode], localNodes: Set[DependencyNode]) = {
    val filteredDuplicateNodes = externalAddedNodes.filterNot(n => {
      localNodes.exists(l => l.baseDependency.coordinates.equalsIgnoringVersion(n.baseDependency.coordinates))
    })
    val filteredExcludedNodes = filteredDuplicateNodes.filterNot(n => {
      localNodes.exists(l => l.baseDependency.exclusions.exists(e => e.equalsCoordinates(n.baseDependency.coordinates)))
    })
    log.debug(s"filteredExcludedNodes count: ${filteredExcludedNodes.size}")
    filteredExcludedNodes
  }

  private def updateLocalNodesWithAddedDepsVersions(addedNodes: Set[DependencyNode], localNodes: Set[DependencyNode], addedDeps: Set[Dependency]) = {
    def updateBaseDependency(localNode: DependencyNode, addedNodes: DependencyNode, addedDeps: Set[Dependency]) = {
      if (addedDeps.exists(localNode.baseDependency.equalsOnCoordinatesIgnoringVersion))
        localNode.baseDependency.withVersion(addedNodes.baseDependency.version)
      else
        localNode.baseDependency
    }

    def updateDependencies(localNode: DependencyNode, addedNode: DependencyNode) = {
      localNode.dependencies ++ addedNode.dependencies.filterNot(node => {
        localNode.baseDependency.exclusions.exists(e => e.equalsCoordinates(node.coordinates))
      })
    }

    localNodes.flatMap(l => {
      val nodeToAdd = addedNodes.find(n => n.baseDependency.coordinates.equalsIgnoringVersion(l.baseDependency.coordinates))
      nodeToAdd.map(n => l.copy(
        baseDependency = updateBaseDependency(l, n, addedDeps),
        dependencies = updateDependencies(l, n)))
    })
  }
}

case class DiffResult(updatedLocalNodes: Set[DependencyNode], localNodes: Set[DependencyNode], managedNodes: Set[DependencyNode])