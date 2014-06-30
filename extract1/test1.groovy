@Grapes([
    @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
    @Grab(group='xerces', module='xercesImpl', version='2.9.1') ])

import groovyx.net.http.*
import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import static groovy.json.JsonOutput.*

// def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/index.html"
// def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/content.html"
def the_base_url = "http://sheffieldairquality.gen2training.co.uk"


println "Loading page"
def response_page = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(the_base_url+'/sheffield/content.html')
  
def map_element = response_page.depthFirst().MAP.find { it.@name="Sheffield" }

println("Process elements...");

map_element.AREA.findAll { area ->
  println("area:: uri::${area.@href} name::${area.@alt}");
  processSensorClusterFrameset(the_base_url, '/sheffield/'+area.@href, area.@alt);
}

println("\n\n All Done");



def processSensorClusterFrameset(base,uri, name) {
  // Fetch page
  try {
    def sensor_cluster_frameset = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(base+uri)
    sensor_cluster_frameset.FRAMESET.FRAME.each {
      if ( it.@name=='left' ) {
        println("Process sensor cluster at"+it.@src);
        processSensorCluster(base,it.@src,name);
      }
    }
  }
  catch ( Exception e ) {
    println("ERROR...."+e.message);
  }
  // println(lhs)
}


def processSensorCluster(base, uri, name) {
  println("Get list of sensors at sensor cluster")
  try {
    def sensor_cluster = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(base+uri)
    println("Got sensor cluster page... find SELECT");
    sensor_cluster_select = sensor_cluster.depthFirst().SELECT.find { it.@name=='ic'}

    sensor_cluster_select.OPTION.each {
      println(it.@value+' '+it.text())
    }
  }
  catch ( Exception e ) {
    println("ERROR...."+e.message);
  }
}
