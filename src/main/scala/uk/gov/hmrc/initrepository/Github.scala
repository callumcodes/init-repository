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

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json._
import play.api.libs.ws.{WSRequest, WSResponse}

import scala.concurrent.Future



class RequestException(request:WSRequest, response:WSResponse)
  extends Exception(s"Got status ${response.status}: ${request.method} ${request.url} ${response.body}"){

}

sealed trait ServiceHookType {
  def name:String
  def domain :String
}

case object Travis extends ServiceHookType{
  override val name: String = "travis"

  override val domain: String = "notify.travis-ci.org"
}


trait Github{

  def githubHttp:GithubHttp

  def githubUrls:GithubUrls

  val IronManApplication = "application/vnd.github.ironman-preview+json"

  def teamId(team: String): Future[Option[Int]]={
    val req = githubHttp.buildJsonCall("GET", githubUrls.teams)


    req.execute().flatMap { res => res.status match {
      case 200 => Future.successful(findIdForName(res.json, team))
      case _   => Future.failed(new RequestException(req, res))
    }}
  }

  def addRepoToTeam(repoName: String, teamId: Int):Future[Unit] = {
    Log.info(s"Adding $repoName to team ${teamId}")

    val req = githubHttp
      .buildJsonCall("PUT", githubUrls.addTeamToRepo(repoName, teamId))
      .withHeaders("Accept" -> IronManApplication)
      .withHeaders("Content-Length" -> "0")
      .withBody("""{"permission": "push"}"""")


    req.execute().flatMap { res => res.status match {
      case 204 => Future.successful(Unit)
      case _   => Future.failed(new RequestException(req, res))
    }}
  }

  def findIdForName(json:JsValue, teamName:String):Option[Int]={
    json.as[JsArray].value
      .find(j => (j \ "name").toOption.exists(s => s.as[JsString].value == teamName))
      .map(j => (j \ "id").get.as[JsNumber].value.toInt)
  }


  def containsRepo(repoName: String): Future[Boolean] = {
    val req = githubHttp.buildJsonCall("GET", githubUrls.containsRepo(repoName))

    req.execute().flatMap { res => res.status match {
      case 200 => Future.successful(true)
      case 404 => Future.successful(false)
      case _   => Future.failed(new RequestException(req, res))
    }}
  }

  def createRepo(repoName: String): Future[String] = {
    Log.info(s"creating github repository with name '${repoName}'")
    val payload = s"""{
                    |    "name": "$repoName",
                    |    "description": "",
                    |    "homepage": "",
                    |    "private": false,
                    |    "has_issues": true,
                    |    "has_wiki": true,
                    |    "has_downloads": true,
                    |    "license_template": "apache-2.0"
                    |}""".stripMargin

      githubHttp.postJsonString(githubUrls.createRepo, payload).map { _ => s"git@github.com:hmrc/$repoName.git" }
  }


  def createServiceHook(repoName: String, serviceType : ServiceHookType): Future[String] = {
    Log.info(s"creating github service hook for repo '$repoName' service name: ${serviceType.name} domain: ${serviceType.domain}")

    val payload = s"""|{"name": "${serviceType.name}",
                      |"active": true,
                      |"events": ["push","pull_request"],
                      |"config":{
                      |     "domain": "${serviceType.domain}",
                      |     "content_type": "json",
                      |     "user": "${githubHttp.creds.user}",
                      |     "token": "${githubHttp.creds.pass}"
                      |     }
                      |}""".stripMargin

    githubHttp.postJsonString(githubUrls.webhook(repoName), payload).map { response =>
      (Json.parse(response) \ "url").as[String]
    }
  }
  
  def close() = githubHttp.close()
}


