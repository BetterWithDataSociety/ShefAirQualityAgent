@Grapes([
    @GrabResolver(name='central', root='http://central.maven.org/maven2/'),
    @Grab(group='org.slf4j', module='slf4j-api', version='1.7.6'),
    @Grab(group='org.slf4j', module='jcl-over-slf4j', version='1.7.6'),
    @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
    @Grab(group='xerces', module='xercesImpl', version='2.9.1'),
    @Grab(group='org.apache.jena', module='jena-tdb', version='1.0.2') 
])

import groovyx.net.http.*
import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import static groovy.json.JsonOutput.*
import virtuoso.jena.driver.*;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.* ;
import com.hp.hpl.jena.graph.*;


// def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/index.html"
// def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/content.html"
def the_base_url = "http://sheffieldairquality.gen2training.co.uk"

def store_uri = "jdbc:virtuoso://localhost:1111"


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

  validateSensorCluster(uri,name);

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

      // For this sensor - work out if we already have a sensor header record in the database

      // http://sheffieldairquality.gen2training.co.uk/cgi-bin/gifgraph_sheffield.cgi/data.txt?format=csv&zmacro=Groundhog1/LD-Groundhog1_NO2.ic&from=000101&to=140630
      def sensorUri = "uri://opensheffield.org/datagrid/sensors/${it.@value}"
      def from="01/01/1900"
      def to="01/06/2014"
      println("DATA URL WILL BE ${base}/cgi-bin/gifgraph_sheffield.cgi/data.txt?format=csv&zmacro=${it.@value}&from=${from}&to=${to}")

      processSensor(sensorUri);
    }
  }
  catch ( Exception e ) {
    println("ERROR...."+e.message);
  }
}

def processSensor(sensorUri) {
  // def graph = new VirtGraph(sensorUri, "jdbc:virtuoso://localhost:1111", "dba", "dba");
  def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");
  // see https://www.google.co.uk/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&cad=rja&uact=8&ved=0CCcQFjAA&url=http%3A%2F%2Fwww.w3.org%2F2005%2FIncubator%2Fssn%2Fwiki%2Fimages%2F2%2F2e%2FSemanticSensorNetworkOntology.pdf&ei=AcexU6e1CY3sO8D6gPAM&usg=AFQjCNHrD8E9qlXEk_bU0kwuLNtNbVC5ng&bvm=bv.69837884,d.ZWU
  // See http://www.w3.org/2005/Incubator/ssn/ssnx/ssn
  Node sensorUri = Node.createURI(sensorUri);
  Node class_sensing_device = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#SensingDevice');
  Node class_observation_value = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#ObservationValue');
  Node class_observation_value = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#ObservationValue');
  Node type_pred = Node.createURI('http://www.w3.org/1999/02/22-rdf-syntax-ns#type');

  graph.add(new Triple(sensorUri, type_pred, class_sensing_device));

  graph.close();
}

def validateSensorCluster(uri, name) {
  println("validateSensorCluster ${uri}, ${name}");
}

def validateSensor(uri, name, type, cluster) {
  println("validateSensor ${uri}, ${name}, ${type}, ${cluster}");
  def result = null;
  result
}

def validateReading(sensorUri, date, time, value) {
  println("validateSensor ${sensorUri}, ${date}, ${time}, ${value}");
}
