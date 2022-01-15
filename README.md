# exists

When you just want to know what exists.

## What does this do?

exists will tell you if a specific artifact exists or it will give you all the
possibilities that _might_ match what you're looking for. As a bonus, if you
looking for versions it'll also tell you if there is a missing
`maven-metadata.xml` file, what the latest version in the metadata file is also
show you if that version doesn't actually match what the latest version _really_
is.

This current works with Maven Central (Sonatype releases), Sonatype snapshots,
and also private Sonatype Nexus installations that you may be using.

It really doesn't do too much.
```
Usage: exists [options] [org[:name[:version]]]

When you just want to know what exists.
Either I'll find it or complete it.

Options:

 -h, --help        shows what you're looking at
 -c, --credentials credentials for the passed in repository
 -r, --repository  specify a repository
```

## Why'd you build this?

The short version was this was built out of annoyance and me going down a rabbit
hole to understand why [Coursier](https://github.com/coursier/coursier) wouldn't
complete snapshots or artifacts from my work's self-hosted Nexus.

The longer version will have to wait until I better understand a few more things
and write them down for you to hopefully enjoy and also have a better
understanding of a few of the following:

  - What's the difference between release and snapshot repository listings?
  - Why doesn't my self-hosted nexus behave just like maven central?
  - Why is supposed to keep this `maven-metadata.xml` up to date anyways?
  - Why is my `maven-metadata.xml` file way out of date on Sonatype releases?

So many fun questions.

For now, feel free to use this in a very similiar way you'd use `cs complete
<artifact>`, but also having it work on sbt plugin published to maven central,
snapshots, and your self-hosted Sonatype nexus.

O, also I got to play with [`scala-cli`](https://scala-cli.virtuslab.org/) for
this, which was a good learning experience.

## Using it

Build it locally
```sh
scala-cli package src/ -o exists
```

Use it for snapshots
```sh
❯ ./exists -r sonatype:snapshots org.scalameta:metals_2.12:
Found up until: org.scalameta:metals_2.12
Latest version according to metadata: 0.10.9+49-60d74a51-SNAPSHOT
Last updated according to metadata: 2021-11-18T20:23:02
Exact match not found, so here are the 10 newest possiblities:
 0.11.0+27-c77d9714-SNAPSHOT
 0.11.0+25-7cc1bc13-SNAPSHOT
 0.11.0+23-bacbde61-SNAPSHOT
 0.11.0+21-8e662d9c-SNAPSHOT
 0.11.0+19-d964693d-SNAPSHOT
 0.11.0+17-69a43f63-SNAPSHOT
 0.11.0+15-ea6ea86c-SNAPSHOT
 0.11.0+13-b6814655-SNAPSHOT
 0.11.0+11-5898e3b5-SNAPSHOT
 0.11.0+10-41d3108e-SNAPSHOT
```

## Don't

Use this in a serious capacity or as a library. Use Coursier instead. Nothing is
being cached here, so you're making a whole bunch of requests since under the
hood we are scraping html/xml. Just use Coursier for anything serious.

