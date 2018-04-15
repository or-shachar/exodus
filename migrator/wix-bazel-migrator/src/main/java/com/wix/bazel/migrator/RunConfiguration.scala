package com.wix.bazel.migrator

import java.io.File
import java.nio.file.{Files, Paths}

case class RunConfiguration(repoRoot: File,
                            managedDepsRepo: File,
                            codotaToken: String,
                            performMavenClasspathResolution: Boolean = true,
                            performTransformation: Boolean = true,
                            failOnSevereConflicts: Boolean = false,
                            interRepoSourceDependency: Boolean = false)

object RunConfiguration {
  private val parser = new scopt.OptionParser[RunConfiguration]("Migrator") {
    head("Wix Bazel Migrator")

    opt[String]('r', "repo")
      .required()
      .withFallback(() => sys.props.get("repo.root").getOrElse(throw new IllegalArgumentException("no repo root defined")))
      .validate(f => if (Files.isDirectory(Paths.get(f))) success else failure(s"repo $f must be existing directory"))
      .action { case (f, cfg) => cfg.copy(repoRoot = new File(f)) }

    opt[String]('r', "managed-deps-repo")
      .required()
      .withFallback(() => sys.props.get("managed.deps.repo").getOrElse(throw new IllegalArgumentException("no managed deps repo defined")))
      .validate(f => if (Files.isDirectory(Paths.get(f))) success else failure(s"repo $f must be existing directory"))
      .action { case (f, cfg) => cfg.copy(managedDepsRepo = new File(f)) }

    opt[String]("codota-token")
      .required()
      .withFallback(() => sys.props.get("codota.token").getOrElse(throw new IllegalArgumentException("no codota token defined")))
      .action { case (token, cfg) => cfg.copy(codotaToken = token) }

    opt[Boolean]("skip-maven")
      .required()
      .withFallback(() => booleanProperty("skip.classpath"))
      .action { case (skip, cfg) => cfg.copy(performMavenClasspathResolution = !skip) }

    opt[Boolean]("skip-transform")
      .required()
      .withFallback(() => booleanProperty("skip.transformation"))
      .action { case (skip, cfg) => cfg.copy(performTransformation = !skip) }

    opt[Boolean]("fail-on-severe-conflicts")
      .required()
      .withFallback(() => booleanProperty("fail.on.severe.conflicts"))
      .action { case (fail, cfg) => cfg.copy(failOnSevereConflicts = fail) }

    opt[Boolean]("inter-repo-source-dependency")
      .withFallback(() => booleanProperty("inter.repo.source.dependency"))
      .action { case (interRepoSourceDependency, cfg) => cfg.copy(interRepoSourceDependency = interRepoSourceDependency) }
  }

  private def booleanProperty(prop: String) = sys.props.get(prop).exists(_.toBoolean)

  def from(cliArgs: Array[String]): RunConfiguration = {
   parser.parse(cliArgs, RunConfiguration(null, null, null)).getOrElse(sys.exit(-1))
  }
}
