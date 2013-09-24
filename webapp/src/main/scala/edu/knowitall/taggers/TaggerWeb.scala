package edu.knowitall.taggers

import scala.collection.JavaConverters._

import edu.knowitall.tool.stem.MorphaStemmer
import edu.knowitall.tool.chunk.OpenNlpChunker

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Planify

// This is a separate class so that optional dependencies are not loaded
// unless a server instance is being create.
class TaggerWeb(port: Int) {
  // NLP tools
  val chunker = new OpenNlpChunker()
  val stemmer = new MorphaStemmer()

  def page(params: Map[String, Seq[String]] = Map.empty, result: String = "") = {
    val sentenceText = params.get("sentences").flatMap(_.headOption).getOrElse("")
    val patternText = params.get("patterns").flatMap(_.headOption).getOrElse("")
    """<html><head><title>Tagger Web</title></head>
       <body><form method='POST'>""" +
         s"<textarea name='patterns' cols='120' rows='25'>$patternText</textarea>" +
         s"<textarea name='sentences' cols='120' rows='25'>$sentenceText</textarea>" +
      """<br />
         <input type='submit'>""" +
         s"<pre>$result</pre>" +
       """</form></body></html>"""
  }

  def run() {
    val plan = Planify {
      case req @ POST(Params(params)) => ResponseString(post(params))
      case req @ GET(_) => ResponseString(page())
    }

    unfiltered.jetty.Http(port).filter(plan).run()
    System.out.println("Server started on port: " + port);
  }

  def post(params: Map[String, Seq[String]]) = {
    val sentenceText = params("sentences").headOption.get
    val patternText = params("patterns").headOption.get

    val rules = ParseRule.parse(patternText).get
    val ctc = rules.foldLeft(new CompactTaggerCollection()){ case (ctc, rule) => ctc + rule }
    val col = ctc.toTaggerCollection

    val results = for (line <- sentenceText.split("\n")) yield {
      val tokens = chunker(line) map stemmer.lemmatizeToken
      val types = col.tag(tokens.asJava).asScala

      (line, types)
    }

    val resultText = results.map { case (sentence, typs) =>
      sentence + "\n" + typs.mkString("\n")
    }.mkString("\n\n")

    page(params, resultText)
  }
}

object TaggerWebMain extends App {
  val server = new TaggerWeb(8080)
  server.run()
}