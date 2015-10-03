package protobufui.cli

import java.io.File
import java.time.LocalDateTime
import javax.xml.bind.{Marshaller, JAXBContext}

import akka.actor.Props
import ipetoolkit.workspace.WorkspaceManagement.LoadWorkspace
import ipetoolkit.workspace.WorkspaceManager
import protobufui.service.source.ClassesLoader.Put
import protobufui.test.format.TestReportCreator
import protobufui.test.format.generated.Testsuites
import protobufui.test.{TestCaseResult, TestSuiteResult}
import protobufui.{Globals, Main}
import protobufui.service.source.ClassesLoader

import scala.concurrent.Await

object CliMain {
  def start(config:Config)={
    val workspaceManager = Main.actorSystem.actorOf(Props(classOf[WorkspaceManager]))
    val workspaceRootPath: String = config.workspace.getAbsolutePath

    workspaceManager ! LoadWorkspace(workspaceRootPath)

    Globals.setProperty(Globals.Keys.workspaceRoot,workspaceRootPath)
    val classesLoader = Main.actorSystem.actorOf(Props(new ClassesLoader(config.workspace)))

    classesLoader ! Put(config.load)

    Thread.sleep(20000)
    val suitesRunner = Main.actorSystem.actorOf(Props(new SuitesRunner(config.suites)))
    val casesRunner = Main.actorSystem.actorOf(Props(new CasesRunner(config.cases)))
    import akka.pattern.ask
    val suitesResultsFuture = (suitesRunner ? RunSuites) mapTo[List[TestSuiteResult]]
    val casesResultsFuture = (casesRunner ? RunCases) mapTo[List[TestCaseResult]]
    import scala.concurrent.duration._
    val suitesResults = Await.result(suitesResultsFuture,30 seconds)
    val casesResults = Await.result(casesResultsFuture,30 seconds)
    val jaxbResult = TestReportCreator.createReport(suitesResults) // todo: dla cas�w


    val reportsRoot = Globals.getProperty(Globals.Keys.workspaceRoot) + File.separator + "reports"

    val jaxbContext = JAXBContext.newInstance(classOf[Testsuites])
    val jaxbMarshaller = jaxbContext.createMarshaller()
    jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    jaxbMarshaller.marshal(jaxbResult, new File(reportsRoot,LocalDateTime.now.toString))
  }
}
