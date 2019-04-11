# Exodus - Maven to Bazel Migration

## Maven has been good to us

Like many developers, you have probably been using Apache Maven to build your projects. We got used to managing those POM (project object model) files and waiting for our projects to build, hoping we had no errors and those pesky dependencies would all work out. 

Maven was good to us when our repositories were a certain size and our projects didn't take hours to build. 

At Wix we realized we needed a new build manager.

## Why Bazel

After evaluating several solutions, **Bazel** was chosen as the best alternative build tool.

### Faster builds and tests
The most significant advantage of Bazel is the speed of the build process. What used to take us xx now takes us yy.

How does Bazel perform so fast?
It uses: 
* Caching - previous results and meta data are cached so they can be skipped on subsequent builds.
* Parallelism - can run compilations and tests in parallel.
* Hermeticity - builds can access only what is declared and pre-fetched, so they run 'hermetically' without unnecesary noise from additional files. 

### Multi-language environment
Here at Wix, we support 19 languages and it's a huge advantage to be able to build multiple languages simultaneously. 

### Scalability
Bazel can handle multiple repositories or any size mono-repo. We are able to scale our codebase and our continuous integration system.

### Extend languages and platforms
Bazel enables us to extend to more languages and platforms using Bazel's familiar extension language. Because there is a growing community of Bazel uses, we can share and re-use language rules written by other teams.

## How to migrate
So now that you're considering migrating from Maven to Bazel, you may be intimated by the manual migration provided in the Bazel [documentation](https://docs.bazel.build/versions/master/migrate-maven.html). 

Good news! We at Wix have created Exodus, an automated migration tool, and are providing you the files and documentation you need to more easily perform the migration.

Links to repo & How to docs.
