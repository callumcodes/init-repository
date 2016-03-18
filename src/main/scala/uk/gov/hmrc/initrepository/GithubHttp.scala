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

import play.api.libs.json._
import play.api.libs.ws._
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient, NingWSClientConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait GithubHttp {

  def creds: ServiceCredentials

  private val ws = new NingWSClient(new NingAsyncHttpClientConfigBuilder(new NingWSClientConfig()).build())

  def close() = {
    ws.close()
    Log.debug("closing github http client")
  }

  def buildJsonCall(method: String, url: String, body: Option[JsValue] = None): WSRequest = {

    val req = ws.url(url)
      .withMethod(method)
      .withAuth(creds.user, creds.pass, WSAuthScheme.BASIC)
      .withHeaders(
        "content-type" -> "application/json")

    Log.debug("req = " + req)

    body.map { b =>
      req.withBody(b)
    }.getOrElse(req)
  }

  def getBody(url: String): Future[String] = {
    get(url).map(_.body)
  }

  def get(url: String): Future[WSResponse] = {
    val resultF = buildJsonCall("GET", url).execute()
    resultF.flatMap { result => result.status match {
      case s if s >= 200 && s < 300 => Future.successful(result)
      case _@e =>
        val msg = s"Didn't get expected status code when writing to Github. Got status ${result.status}: GET ${url} ${result.body}"
        Log.error(msg)
        Future.failed(new scala.Exception(msg))
    }
    }
  }

  def postJsonString(url: String, body: String): Future[String] = {
    buildJsonCall("POST", url, Some(Json.parse(body))).execute().flatMap { case result =>
      result.status match {
        case s if s >= 200 && s < 300 => Future.successful(result.body)
        case _@e =>
          val msg = s"Didn't get expected status code when writing to Github. Got status ${result.status}: POST ${url} ${result.body}"
          Log.error(msg)
          Future.failed(new scala.Exception(msg))
      }
    }
  }
}
