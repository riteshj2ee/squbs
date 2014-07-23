package org.squbs.httpclient.jmx

import org.scalatest._
import org.squbs.httpclient.{HttpClientFactory}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver, EndpointRegistry}
import akka.actor.ActorSystem
import org.squbs.httpclient.pipeline.Pipeline
import spray.client.pipelining._
import scala.concurrent.duration._
import org.squbs.httpclient.config.Configuration
import org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler
import org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler
import spray.http.HttpHeaders.RawHeader
import spray.can.Http.ClientConnectionType.Proxied
import scala.Some
import akka.io.IO
import spray.can.Http
import akka.pattern._
import spray.util._
import spray.can.client.{ClientConnectionSettings, HostConnectorSettings}
import org.squbs.httpclient.env.{Default, Environment}

/**
 * Created by hakuang on 6/10/2014.
 */
class HttpClientJMXSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll{

  private implicit val system = ActorSystem("JMXSpec")

  override def beforeEach = {
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = Some(Endpoint("http://www.ebay.com"))
      override def name: String = "hello"
    })
  }

  override def afterEach = {
    EndpointRegistry.endpointResolvers.clear
    HttpClientFactory.httpClientMap.clear
  }

  override def afterAll() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }

  "HttpClient with svcName" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello1")
    HttpClientFactory.getOrCreate("hello2")
    HttpClientBean.getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello1") should be (HttpClientInfo("hello1", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello2") should be (HttpClientInfo("hello2", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient with pipeline" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello3", pipeline = Some(RequestResponsePipeline))
    HttpClientFactory.getOrCreate("hello4")
    HttpClientBean.getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello3") should be (HttpClientInfo("hello3", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler$$anonfun$processRequest$1","org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler$$anonfun$processResponse$1"))
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello4") should be (HttpClientInfo("hello4", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient with configuration" should "show up the correct value of HttpClientBean" in {
    val httpClient = HttpClientFactory.getOrCreate("hello5")
    httpClient.updateConfig(Configuration(hostSettings = HostConnectorSettings(10 ,10, 10, true, 10 seconds, ClientConnectionSettings(system)), connectionType = Proxied("www.ebay.com", 80)))
    HttpClientFactory.getOrCreate("hello6").markDown
    HttpClientBean.getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello5") should be (HttpClientInfo("hello5", "default", "http://www.ebay.com", "UP", "www.ebay.com:80", 10, 10, 10, 20000, 10000, "", ""))
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello6") should be (HttpClientInfo("hello6", "default", "http://www.ebay.com", "DOWN", "www.ebay.com:80", 10, 10, 10, 20000, 10000, "", ""))
  }

  def findHttpClientBean(beans: java.util.List[HttpClientInfo], name: String): HttpClientInfo = {
    import scala.collection.JavaConversions._
    beans.toList.find(_.name == name).get
  }
}

object RequestResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("request1-name", "request1-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("response1-name", "response1-value")).processResponse)
}