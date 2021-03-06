/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.core.assertion

import scala.tools.nsc.io.Path

import io.gatling.core.result.{ Group, StatsPath, GroupStatsPath, RequestStatsPath }
import io.gatling.core.result.message.{ KO, OK, Status }
import io.gatling.core.result.reader.{ DataReader, GeneralStats }
import io.gatling.core.config.GatlingConfiguration.configuration
import io.gatling.core.util.NumberHelper

class Selector(stats: (DataReader, Option[Status]) => GeneralStats, name: String) {
	def responseTime = new ResponseTime(reader => stats(reader, None), name)

	def allRequests = new Requests(stats, None, name)

	def failedRequests = new Requests(stats, Some(KO), name)

	def successfulRequests = new Requests(stats, Some(OK), name)

	def requestsPerSec = Metric(reader => stats(reader, None).meanRequestsPerSec, s"$name: requests per second")
}

object ResponseTime {
	val percentile1 = NumberHelper.formatNumberWithSuffix(configuration.charting.indicators.percentile1)
	val percentile2 = NumberHelper.formatNumberWithSuffix(configuration.charting.indicators.percentile2)
}

class ResponseTime(responseTime: DataReader => GeneralStats, name: String) {
	def min = Metric(reader => responseTime(reader).min, s"$name: min response time")

	def max = Metric(reader => responseTime(reader).max, s"$name: max response time")

	def mean = Metric(reader => responseTime(reader).mean, s"$name: mean response time")

	def stdDev = Metric(reader => responseTime(reader).stdDev, s"$name: standard deviation response time")

	def percentile1 = Metric(reader => responseTime(reader).percentile1, s"$name: ${ResponseTime.percentile1} percentile response time")

	def percentile2 = Metric(reader => responseTime(reader).percentile2, s"$name: ${ResponseTime.percentile2} percentile response time")
}

class Requests(requests: (DataReader, Option[Status]) => GeneralStats, status: Option[Status], name: String) {

	private def message(message: String) = status match {
		case Some(status) => s"$name $message $status"
		case None => s"$name $message"
	}

	def percent = Metric(reader => math.round((requests(reader, status).count.toFloat / requests(reader, None).count) * 100), message("percentage of requests"))

	def count = Metric(reader => requests(reader, status).count, message("number of requests"))
}

case class Metric[T: Numeric](value: DataReader => T, name: String, assertions: List[Assertion] = List()) {
	def assert(assertion: (T) => Boolean, message: (String, Boolean) => String) = copy(assertions = assertions :+ new Assertion(reader => assertion(value(reader)), result => message(name, result)))

	def lessThan(threshold: T) = assert(implicitly[Numeric[T]].lt(_, threshold), (name, result) => s"$name is less than $threshold: $result")

	def greaterThan(threshold: T) = assert(implicitly[Numeric[T]].gt(_, threshold), (name, result) => s"$name is greater than $threshold: $result")

	def between(min: T, max: T) = assert(v => implicitly[Numeric[T]].gteq(v, min) && implicitly[Numeric[T]].lteq(v, max), (name, result) => s"$name between $min and $max: $result")

	def is(v: T) = assert(_ == v, (name, result) => s"$name is equal to $v: $result")

	def in(set: Set[T]) = assert(set.contains, (name, result) => s"$name is in $set")
}

object Assertion {
	def assertThat(assertions: Seq[Assertion], dataReader: DataReader) =
		assertions.foldLeft(true)((result, assertion) => assertion(dataReader) && result)
}

class Assertion(assertion: (DataReader) => Boolean, message: (Boolean) => String) {
	def apply(reader: DataReader) = {
		val result = assertion(reader)
		println(message(result))
		result
	}
}
