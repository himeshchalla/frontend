package model

import com.gu.contentapi.client.model.v1.{Content => ApiContent, Tag => ApiTag, TagType}
import com.gu.contentapi.client.utils.CapiModelEnrichment.RichJodaDateTime
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class UrlsTest extends FlatSpec with Matchers with GuiceOneAppPerSuite {

  "Urls" should "be created relative for articles" in {

    val content = ApiContent(id = "foo/2012/jan/07/bar",
      sectionId = None,
      sectionName = None,
      webPublicationDate = Some(new DateTime().toCapiDateTime),
      webTitle = "Some article",
      webUrl = "http://www.guardian.co.uk/foo/2012/jan/07/bar",
      apiUrl = "http://content.guardianapis.com/foo/2012/jan/07/bar",
      tags = List(tag("type/article")),
      elements = None
    )

    SupportedUrl(content) should be("/foo/2012/jan/07/bar")

    Content(content).metadata.url should be("/foo/2012/jan/07/bar")
  }

  they should "be created relative for galleries" in {

    val content = ApiContent(id = "foo/gallery/2012/jan/07/bar",
      sectionId = None,
      sectionName = None,
      webPublicationDate = Some(new DateTime().toCapiDateTime),
      webTitle = "Some article",
      webUrl = "http://www.guardian.co.uk/foo/gallery/2012/jan/07/bar",
      apiUrl = "http://content.guardianapis.com/foo/gallery/2012/jan/07/bar",
      tags = List(tag("type/gallery")),
      elements = None
    )

    SupportedUrl(content) should be("/foo/gallery/2012/jan/07/bar")

    Content(content).metadata.url should be("/foo/gallery/2012/jan/07/bar")
  }

  they should "be created relative for tags" in {
    Tag.make(tag("foo/bar")).metadata.url should be("/foo/bar")
  }

  private def tag(id: String, name: String = "") = ApiTag(
    id = id, `type` = TagType.Type, webTitle = name, webUrl = "", apiUrl = ""
  )
}
