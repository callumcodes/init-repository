/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.initrepository

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{MappingBuilder, RequestPatternBuilder, ResponseDefinitionBuilder}
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json



class GithubSpecs extends WordSpec with Matchers with FutureValues with WireMockEndpoints {


  val gituser = "gituser"
  val gittoken = "gittoken"
  class FakeGithubHttp extends GithubHttp {
    override def creds: ServiceCredentials = ServiceCredentials(gituser, gittoken)
  }

  val github: Github = new Github{
    override def githubHttp: GithubHttp = new FakeGithubHttp()

    override def githubUrls: GithubUrls = new GithubUrls(apiRoot = endpointMockUrl)
  }

  "Github.containsRepo" should {

    "return true when github returns 200" in {

      givenGitHubExpects(
        method = GET,
        url = "/repos/hmrc/domain",
        willRespondWith = (200, None)
      )

      github.containsRepo("domain").await shouldBe true
    }

    "return false when github returns 404" in {

      givenGitHubExpects(
        method = GET,
        url = "/repos/hmrc/domain",
        willRespondWith = (404, None)
      )

      github.containsRepo("domain").await shouldBe false
    }

    "throw exception when github returns anything other than 200 or 404" in {

      givenGitHubExpects(
        method = GET,
        url = "/repos/hmrc/domain",
        willRespondWith = (999, None)
      )

      intercept[RequestException]{
        github.containsRepo("domain").await
      }
    }
  }

  "Github.teamId" should {
    "find a team ID for a team name when the team exists" in {
      givenGitHubExpects(
        method = GET,
        url = "/orgs/hmrc/teams?per_page=100",
        willRespondWith = (200, Some(
          """
            |[
            |  {
            |    "id": 1,
            |    "url": "https://api.github.com/teams/1",
            |    "name": "Justice League"
            |  },
            |  {
            |    "id": 2,
            |    "url": "https://api.github.com/teams/1",
            |    "name": "Auth"
            |  }
            |]
          """.stripMargin))
      )

      printMappings
      github.teamId("Auth").await.get shouldBe 2
    }

    "return None when the team does not exist" in {
      givenGitHubExpects(
        method = GET,
        url = "/orgs/hmrc/teams?per_page=100",
        willRespondWith = (200, Some(
          """
            |[
            |  {
            |    "id": 1,
            |    "url": "https://api.github.com/teams/1",
            |    "name": "Justice League"
            |  },
            |  {
            |    "id": 2,
            |    "url": "https://api.github.com/teams/2",
            |    "name": "Auth"
            |  }
            |]
          """.stripMargin))
      )

      printMappings
      github.teamId("MDTP").await shouldBe None
    }
  }

  "Github.createRepo" should {

    "successfully create repo" in {

      givenGitHubExpects(
        method = POST,
        url = "/orgs/hmrc/repos",
        willRespondWith = (201, None)
      )

      val createdUrl = github.createRepo("domain").await

      createdUrl shouldBe "git@github.com:hmrc/domain.git"

      assertRequest(
        method = POST,
        url = "/orgs/hmrc/repos",
        jsonBody = Some("""{
                      |    "name": "domain",
                      |    "description": "",
                      |    "homepage": "",
                      |    "private": false,
                      |    "has_issues": true,
                      |    "has_wiki": true,
                      |    "has_downloads": true,
                      |    "license_template": "apache-2.0"
                      |}""".stripMargin)
      )
    }
  }

  "Github.createServiceHook" should {

    "successfully reate service hook" in {

      val createServiceHookResponse =
        """
          |{
          |   "id": 1,
          |   "url": "https://api.github.com/repos/hmrc/domain/hooks/1",
          |   "test_url": "https://api.github.com/repos/hmrc/domain/hooks/1/test",
          |   "ping_url": "https://api.github.com/repos/hmrc/domain/hooks/1/pings"
          |}
        """.stripMargin


      givenGitHubExpects(
        method = POST,
        url = "/repos/hmrc/domain/hooks",
        willRespondWith = (201, Some(createServiceHookResponse))
      )

      val hookUrl = github.createServiceHook("domain", Travis).await

      hookUrl shouldBe "https://api.github.com/repos/hmrc/domain/hooks/1"

      assertRequest(
        method = POST,
        url = "/repos/hmrc/domain/hooks",
        jsonBody = Some(s"""
                      |{"name": "travis",
                      |"active": true,
                      |"events": ["push","pull_request"],
                      |"config":{
                      |     "domain": "notify.travis-ci.org",
                      |     "content_type": "json",
                      |     "user": "$gituser",
                      |     "token": "$gittoken"
                      |     }
                      |}""".stripMargin)
      )
    }
  }




  "Github.addRepoToTeam" should {
    "add a repository to a team in" in {
      givenGitHubExpects(
        method = PUT,
        url = "/teams/99/repos/hmrc/domain?permission=push",
        willRespondWith = (204, None)
      )

      printMappings
      github.addRepoToTeam("domain", 99).awaitSuccess()

      assertRequest(
        method = PUT,
        url = "/teams/99/repos/hmrc/domain?permission=push",
        extraHeaders = Map("Accept" -> "application/vnd.github.ironman-preview+json"),
        jsonBody = Some("""{"permission": "push"}""")
      )
    }
  }

  case class GithubRequest(method:RequestMethod, url:String, body:Option[String]){

    {
      body.foreach { b => Json.parse(b) }
    }

    def req:RequestPatternBuilder = {
      val builder = new RequestPatternBuilder(method, urlEqualTo(url))
      body.map{ b =>
        builder.withRequestBody(equalToJson(b))
      }.getOrElse(builder)
    }
  }

  def assertRequest(
                     method:RequestMethod,
                     url:String,
                     extraHeaders:Map[String,String] = Map(),
                     jsonBody:Option[String]): Unit ={
    val builder = new RequestPatternBuilder(method, urlEqualTo(url))
    extraHeaders.foreach { case(k, v) =>
      builder.withHeader(k, equalTo(v))
    }

    jsonBody.map{ b =>
      builder.withRequestBody(equalToJson(b))
    }.getOrElse(builder)
    endpointMock.verifyThat(builder)
  }

  def assertRequest(req:GithubRequest): Unit ={
    endpointMock.verifyThat(req.req)
  }

  def givenGitHubExpects(
                          method:RequestMethod,
                          url:String,
                          extraHeaders:Map[String,String] = Map(),
                          willRespondWith: (Int, Option[String])): Unit = {

    val builder = new MappingBuilder(method, urlEqualTo(url))
      .withHeader("Content-Type", equalTo("application/json"))

    val response: ResponseDefinitionBuilder = new ResponseDefinitionBuilder()
      .withStatus(willRespondWith._1)

    val resp = willRespondWith._2.map { b =>
      response.withBody(b)
    }.getOrElse(response)

    builder.willReturn(resp)

    endpointMock.register(builder)
  }
}
