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

package uk.gov.hmrc.initrepository

import java.net.URL

import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GithubUrls(orgName: String = "hmrc",
                 apiRoot: String = "https://api.github.com") {

  val PAGE_SIZE = 100

  def createRepo: URL =
    new URL(s"$apiRoot/orgs/$orgName/repos")

  def containsRepo(repo: String) =
    new URL(s"$apiRoot/repos/$orgName/$repo")

  def teams(page: Int = 1) =
    new URL(s"$apiRoot/orgs/$orgName/teams?per_page=$PAGE_SIZE&page=$page")

  def addTeamToRepo(repoName: String, teamId: Int) =
    new URL(s"$apiRoot/teams/$teamId/repos/$orgName/$repoName?permission=push")
}

trait Github {

  def httpTransport: HttpTransport

  def githubUrls: GithubUrls

  val IronManApplication = "application/vnd.github.ironman-preview+json"

  def teamId(teamName: String): Future[Option[Int]] = {
    allTeams().map { teams =>
      teams.find(_.name == teamName).map(_.id)
    }
  }

  private def allTeams(page: Int = 1): Future[Seq[Team]] = {

    implicit val format = Json.format[Team]

    val req = httpTransport.buildJsonCallWithAuth("GET", githubUrls.teams(page))

    val aPageOfTeams = req.execute().flatMap { res =>
      res.status match {
        case 200 => Future.successful(res.json.as[Seq[Team]])
        case _ => Future.failed(new RequestException(req, res))
      }
    }

    aPageOfTeams.flatMap { currentPage =>
      if (currentPage.size == githubUrls.PAGE_SIZE) {
        allTeams(page + 1).map { nextPage => currentPage ++ nextPage }
      } else Future.successful(currentPage)
    }
  }

  def addRepoToTeam(repoName: String, teamId: Int):Future[Unit] = {
    Log.info(s"Adding $repoName to team ${teamId}")

    val req = httpTransport
      .buildJsonCallWithAuth("PUT", githubUrls.addTeamToRepo(repoName, teamId))
      .withHeaders("Accept" -> IronManApplication)
      .withHeaders("Content-Length" -> "0")
      .withBody("""{"permission": "push"}"""")

    Log.debug(req.toString)

    req.execute().flatMap { res => res.status match {
      case 204 => Future.successful(Unit)
      case _   => Future.failed(new RequestException(req, res))
    }}
  }

  def findIdForName(json: JsValue, teamName: String): Option[Int] = {
    json.as[JsArray].value
      .find(j => (j \ "name").toOption.exists(s => s.as[JsString].value == teamName))
      .map(j => (j \ "id").get.as[JsNumber].value.toInt)
  }


  def containsRepo(repoName: String): Future[Boolean] = {
    val req = httpTransport.buildJsonCallWithAuth("GET", githubUrls.containsRepo(repoName))

    Log.debug(req.toString)

    req.execute().flatMap { res => res.status match {
      case 200 => Future.successful(true)
      case 404 => Future.successful(false)
      case _   => Future.failed(new RequestException(req, res))
    }}
  }

  def createRepo(repoName: String, privateRepo: Boolean): Future[String] = {
    Log.info(s"creating github repository with name '$repoName'")
    val payload = s"""{
                    |    "name": "$repoName",
                    |    "description": "",
                    |    "homepage": "",
                    |    "private": $privateRepo,
                    |    "has_issues": true,
                    |    "has_wiki": true,
     ${if(!privateRepo)""""license_template": "apache-2.0",""" else ""}
                    |    "has_downloads": true
                    |}""".stripMargin

    val url = githubUrls.createRepo
    val req = httpTransport.buildJsonCallWithAuth("POST", url, Some(Json.parse(payload)))

    Log.debug(req.toString)

    req.execute().flatMap { case result =>
      result.status match {
        case s if s >= 200 && s < 300 => Future.successful(s"git@github.com:hmrc/$repoName.git")
        case _@e => Future.failed(new scala.Exception(
          s"Didn't get expected status code when writing to ${url}. Got status ${result.status}: POST ${url} ${result.body}"))
      }
    }
  }

  def close() = httpTransport.close()
}

case class SimpleResponse(status: Int, rawBody: String)

case class Team(name: String, id: Int)
