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

class GithubUrls( orgName:String = "hmrc",
                  apiRoot:String = "https://api.github.com"){

  def createRepo = s"$apiRoot/orgs/$orgName/repos"

  def containsRepo(repo:String) = s"$apiRoot/repos/$orgName/$repo"

  def teams = s"$apiRoot/orgs/$orgName/teams?per_page=100"

  def addTeamToRepo(repoName:String, teamId:Int) = s"$apiRoot/teams/$teamId/repos/$orgName/$repoName?permission=push"

  def webhook(repoName: String) = s"$apiRoot/repos/$orgName/$repoName/hooks"
}
