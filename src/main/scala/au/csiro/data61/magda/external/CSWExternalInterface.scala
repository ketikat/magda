package au.csiro.data61.magda.external

import java.io.IOException

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.xml.NodeSeq

import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext
import au.csiro.data61.magda.model.temporal._
import au.csiro.data61.magda.model.misc._
import au.csiro.data61.magda.util.DateParser._
import java.time.Instant
import com.monsanto.labs.mwundo.GeoJson.Coordinate
import com.monsanto.labs.mwundo.GeoJson.Polygon
import com.monsanto.labs.mwundo.GeoJson.MultiPolygon
import com.monsanto.labs.mwundo.GeoJson.Point
import com.monsanto.labs.mwundo.GeoJson.MultiPoint

class CSWExternalInterface(interfaceConfig: InterfaceConfig, implicit val system: ActorSystem, implicit val executor: ExecutionContext, implicit val materializer: Materializer) extends ExternalInterface with ScalaXmlSupport {
  val logger = Logging(system, getClass)
  implicit val fetcher = new HttpFetcher(interfaceConfig, system, materializer, executor)

  def getDataSets(start: Long = 0, number: Int = 10): Future[List[DataSet]] = {
    val query = s"""/geonetwork/srv/eng/csw?service=CSW&version=2.0.2&request=GetRecords&constraintlanguage=CQL_TEXT&resultType=results&elementsetname=full&startPosition=${start + 1}&maxRecords=$number""";

    fetcher.request(query).flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[NodeSeq].map(responseConv(_))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"CSW request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  def getTotalDataSetCount(): Future[Long] = {
    val query = s"""/geonetwork/srv/eng/csw?service=CSW&version=2.0.2&request=GetRecords&constraintlanguage=CQL_TEXT&maxRecords=1""";

    fetcher.request(query).flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[NodeSeq].map(res => (res \ "SearchResults" \@ "numberOfRecordsMatched").toLong)
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"CSW request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  implicit def responseConv(res: NodeSeq): List[DataSet] = {
    dataSetConv(res \ "SearchResults" \ "Record")
  }

  implicit def nodeToOption[T](node: NodeSeq, toValue: NodeSeq => T): Option[T] =
    if (node.size == 0) None else Some(toValue(node))

  implicit def nodeToStringOption(node: NodeSeq): Option[String] = nodeToOption(node, x => x.text)

  implicit def dataSetConv(res: NodeSeq) =
    res map { summaryRecord =>
      val modified = nodeToStringOption(summaryRecord \ "date").flatMap(parseDate(_, false) match {
        case InstantResult(instant) => Some(instant)
        case ConstantResult(constant) => constant match {
          case Now => Some(Instant.now())
        }
        case ParseFailure =>
          logger.debug("Parse failure for {}", summaryRecord \ "date" text)
          None
      })

      DataSet(
        identifier = summaryRecord \ "identifier" text,
        catalog = "CSW",
        title = summaryRecord \ "title",
        description = nodeToStringOption(summaryRecord \ "description").orElse(summaryRecord \ "abstract"),
        modified = modified,
        language = summaryRecord \ "language",
        publisher = nodeToStringOption(summaryRecord \ "publisher")
          .orElse(nodeToStringOption(summaryRecord \ "custodian"))
          .map(name => Agent(name = Some(name))),
        spatial = nodeToOption(summaryRecord \ "BoundingBox", identity).flatMap(locationFromBoundingBox),
        temporal = nodeToOption(summaryRecord \ "coverage", identity).flatMap(temporalFromString(modified)),
        theme = (summaryRecord \ "subject").map(_.text.trim),
        distributions = buildDistributions(nodesWithAttribute(summaryRecord \ "URI", "name", "File download"), summaryRecord \ "rights"),
        landingPage = nodesWithAttribute(summaryRecord \ "URI", "protocol", "WWW:LINK-1.0-http--metadata-URL")
          .headOption.map(_.text)
      )
    } toList
//
  def nodesWithAttribute(sourceNodes: NodeSeq, name: String, value: String): NodeSeq = {
    sourceNodes.filter(node => node.attribute(name).map(_.text.trim == value).getOrElse(false))
  }

  def buildDistributions(uriNodes: NodeSeq, rightsNodes: NodeSeq): Seq[Distribution] = {
    val rights = rightsNodes.map(_.text.trim).distinct match {
      case Seq(x) => Some(x)
      case _      => None
    }

    uriNodes.map { node =>
      val url = node.text.trim
      val format = Distribution.parseFormat(None, Some(url), None)

      Distribution(
        title = format match {
          case Some(formatString) => s"Download as $formatString"
          case None               => "Download"
        },
        description = Some(node \@ "description"),
        format = format,
        rights = rights.map(right => License(name = Some(right))),
        downloadURL = Some(url)
      )
    }
  }

  val temporalCoveragePattern = """.*start="(.*)"; end="(.*)"""".r
  def temporalFromString(modified: Option[Instant])(nodes: NodeSeq): Option[PeriodOfTime] = {
    val dates = nodes.map { node =>
      val text = node.text.trim
      text match {
        case temporalCoveragePattern(startTime, endTime) => PeriodOfTime.parse(Some(startTime), Some(endTime), modified)
        case "" => None
        case _ => Some(PeriodOfTime(start = Some(ApiInstant(text = text))))
      }
    }.flatMap {
      case Some(periodOfTime) => Seq(periodOfTime.start, periodOfTime.end)
      case None               => Seq()
    }.flatten
      .filter(_.date.isDefined)
      .sortBy(_.date.get.getEpochSecond)

    dates match {
      case Seq()             => None
      case Seq(date)         => Some(PeriodOfTime(start = Some(date)))
      case Seq(date1, date2) => Some(PeriodOfTime(start = Some(date1), end = Some(date2)))
      case seq               => Some(PeriodOfTime(start = Some(seq.head), end = Some(seq.last)))
    }
  }

  def locationFromBoundingBox(node: NodeSeq): Option[Location] = {
    def toCoord(array: Array[String]) = Coordinate(BigDecimal(array(0)), BigDecimal(array(1)))
    def toCoords(node: NodeSeq): (Coordinate, Coordinate) = {
      val upperLeftCorner = toCoord((node \ "UpperCorner" text).split(" "))
      val lowerRightCorner = toCoord((node \ "LowerCorner" text).split(" "))
      (upperLeftCorner, lowerRightCorner)
    }
    def toPolygonCoords(upperLeftCorner: Coordinate, lowerRightCorner: Coordinate): Seq[Seq[Coordinate]] = {
      val upperRightCorner = Coordinate(lowerRightCorner.x, upperLeftCorner.y)
      val lowerLeftCorner = Coordinate(upperLeftCorner.x, lowerRightCorner.y)

      Seq(
        Seq(
          upperLeftCorner,
          upperRightCorner,
          lowerRightCorner,
          lowerLeftCorner,
          upperLeftCorner
        )
      )
    }

    try {
      val geometry =
        if (node.size == 1) {
          val (upperLeftCorner, lowerRightCorner) = toCoords(node)
          Polygon(toPolygonCoords(upperLeftCorner, lowerRightCorner))
        } else {
          val (coords, polygons) = node.foldRight((List[Coordinate](), List[Seq[Seq[Coordinate]]]())) {
            case (innerNode, (coords, polygons)) =>
              val (upperLeftCorner, lowerRightCorner) = toCoords(innerNode)

              if (upperLeftCorner.equals(lowerRightCorner)) {
                (upperLeftCorner :: coords, polygons)
              } else {
                (coords, toPolygonCoords(upperLeftCorner, lowerRightCorner) :: polygons)
              }
          }

          if (!polygons.isEmpty) MultiPolygon(polygons)
          else {
            coords.distinct match {
              case Seq(singleCoord)                => Point(singleCoord)
              case Seq(coord1, coord2)             => Polygon(toPolygonCoords(coord1, coord2))
              case distinctCoords: Seq[Coordinate] => MultiPoint(distinctCoords)
            }
          }
        }

      Some(Location.applySanitised(
        text = node.text.trim,
        geoJson = Some(geometry)
      ))
    } catch {
      case e: Throwable =>
        logger.warning("Could not parse bounding box '{}'", node.text.trim)
        Some(Location(
          text = node.text,
          geoJson = None
        ))
    }
  }
}