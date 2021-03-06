/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.initrepository.git

import org.mockito.Matchers.any
import org.mockito.Mockito.{verify, verifyNoMoreInteractions, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.initrepository.RepositoryType.Sbt

import scala.util.Try

class LocalGitServiceSpec extends WordSpec with Matchers with MockitoSugar {


  "LocalGitService" should {
    "format the readme for a private repository" in {
      new LocalGitService(mock[LocalGitStore]).buildReadmeTemplate(
        repoName = "myRepo",
        repositoryType = Sbt,
        enableTravis = false,
        privateRepo = true
      ) shouldBe
        """
          |# myRepo
          |
          |This is a placeholder README.md for a new repository
          |""".stripMargin
    }

    "format the readme for a public repository" in {
      new LocalGitService(mock[LocalGitStore]).buildReadmeTemplate(
        repoName = "myRepo",
        repositoryType = Sbt,
        enableTravis = false,
        privateRepo = false
      ) shouldBe
        s"""
           |# myRepo
           |
           | [ ![Download](https://api.bintray.com/packages/hmrc/releases/myRepo/images/download.svg) ](https://bintray.com/hmrc/releases/myRepo/_latestVersion)
           |
           |This is a placeholder README.md for a new repository
           |
           |### License
           |
           |This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
           |""".stripMargin
    }

    "format the readme for a public repository integrated with travis" in {
      new LocalGitService(mock[LocalGitStore]).buildReadmeTemplate(
        repoName = "myRepo",
        repositoryType = Sbt,
        enableTravis = true,
        privateRepo = false
      ) shouldBe
        s"""
           |# myRepo
           |
           |[![Build Status](https://travis-ci.org/hmrc/myRepo.svg?branch=master)](https://travis-ci.org/hmrc/myRepo) [ ![Download](https://api.bintray.com/packages/hmrc/releases/myRepo/images/download.svg) ](https://bintray.com/hmrc/releases/myRepo/_latestVersion)
           |
           |This is a placeholder README.md for a new repository
           |
           |### License
           |
           |This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
           |""".stripMargin
    }
  }

  "initialiseRepository" should {
    "initialise a private repository" in {
      val store = mock[LocalGitStore]
      val service = new LocalGitService(store)

      when(store.cloneRepoURL(any[String])).thenReturn(Try((): Unit))
      when(store.commitFileToRoot(any[String], any[String], any[Option[String]], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.commitFileToRoot(any[String], any[String], any[String], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.lastCommitSha(any[String])).thenReturn(Try(Some("abcd")))
      when(store.tagAnnotatedCommit(any[String], any[String], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.push(any[String])).thenReturn(Try((): Unit))
      when(store.pushTags(any[String])).thenReturn(Try((): Unit))

      service.initialiseRepository(
        repoUrl = "git@github.com:hmrc/a-service.git",
        repositoryType = Sbt,
        bootstrapVersion = "0.1.0",
        digitalServiceName = None,
        enableTravis = false,
        privateRepo = true
      )

      verify(store).cloneRepoURL("git@github.com:hmrc/a-service.git")
      verify(store).commitFileToRoot("a-service", ".gitignore", service.gitIgnoreContents, "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", "README.md", service.buildReadmeTemplate("a-service", Sbt, false, true), "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", "repository.yaml", None, "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).push("a-service")

      verifyNoMoreInteractions(store)
    }

    "initialise a public repository" in {
      val store = mock[LocalGitStore]
      val service = new LocalGitService(store)
      when(store.cloneRepoURL(any[String])).thenReturn(Try((): Unit))
      when(store.commitFileToRoot(any[String], any[String], any[Option[String]], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.commitFileToRoot(any[String], any[String], any[String], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.lastCommitSha(any[String])).thenReturn(Try(Some("abcd")))
      when(store.tagAnnotatedCommit(any[String], any[String], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.push(any[String])).thenReturn(Try((): Unit))
      when(store.pushTags(any[String])).thenReturn(Try((): Unit))


      service.initialiseRepository(
        repoUrl = "git@github.com:hmrc/a-service.git",
        repositoryType = Sbt,
        bootstrapVersion = "0.1.0",
        digitalServiceName = None,
        enableTravis = false,
        privateRepo = false
      )

      verify(store).cloneRepoURL("git@github.com:hmrc/a-service.git")
      verify(store).commitFileToRoot("a-service", ".gitignore", service.gitIgnoreContents, "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", "README.md", service.buildReadmeTemplate("a-service", Sbt, false, false), "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", "repository.yaml", None, "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).lastCommitSha("a-service")
      verify(store).tagAnnotatedCommit("a-service", "abcd", "Bootstrap tag", "v0.1.0")
      verify(store).push("a-service")
      verify(store).pushTags("a-service")
    }
    "initialise a public repository with travis and" in {
      val store = mock[LocalGitStore]
      val service = new LocalGitService(store)
      when(store.cloneRepoURL(any[String])).thenReturn(Try((): Unit))
      when(store.commitFileToRoot(any[String], any[String], any[Option[String]], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.commitFileToRoot(any[String], any[String], any[String], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.lastCommitSha(any[String])).thenReturn(Try(Some("abcd")))
      when(store.tagAnnotatedCommit(any[String], any[String], any[String], any[String])).thenReturn(Try((): Unit))
      when(store.push(any[String])).thenReturn(Try((): Unit))
      when(store.pushTags(any[String])).thenReturn(Try((): Unit))


      service.initialiseRepository(
        repoUrl = "git@github.com:hmrc/a-service.git",
        repositoryType = Sbt,
        bootstrapVersion = "0.1.0",
        digitalServiceName = None,
        enableTravis = true,
        privateRepo = false
      )

      verify(store).cloneRepoURL("git@github.com:hmrc/a-service.git")
      verify(store).commitFileToRoot("a-service", ".gitignore", service.gitIgnoreContents, "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", ".travis.yml", service.buildTravisYamlTemplate("a-service"), "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", "README.md", service.buildReadmeTemplate("a-service", Sbt, true, false), "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).commitFileToRoot("a-service", "repository.yaml", None, "hmrc-web-operations", "hmrc-web-operations@digital.hmrc.gov.uk")
      verify(store).lastCommitSha("a-service")
      verify(store).tagAnnotatedCommit("a-service", "abcd", "Bootstrap tag", "v0.1.0")
      verify(store).push("a-service")
      verify(store).pushTags("a-service")
    }
  }
}
