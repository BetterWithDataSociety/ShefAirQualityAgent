@Grapes([
    // @GrabResolver(name='central', root='http://central.maven.org/maven2/'),
    @Grab(group='org.slf4j', module='slf4j-api', version='1.7.6'),
    @Grab(group='org.slf4j', module='jcl-over-slf4j', version='1.7.6'),
    @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
    @Grab(group='xerces', module='xercesImpl', version='2.9.1'),
    @Grab(group='org.apache.jena', module='jena-tdb', version='1.0.2'),
    @Grab(group='org.apache.jena', module='jena-core', version='2.11.2'),
    @Grab(group='org.apache.jena', module='jena-arq', version='2.11.2'),
    @Grab(group='org.apache.jena', module='jena-iri', version='1.0.2'),
    @Grab(group='org.apache.jena', module='jena-spatial', version='1.0.1'),
    @Grab(group='org.apache.jena', module='jena-security', version='2.11.2'),
    @Grab(group='org.apache.jena', module='jena-text', version='1.0.1'),
    @Grab(group='virtuoso', module='virtjena', version='2'),
    @Grab(group='virtuoso', module='virtjdbc', version='4.1')
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
import java.text.SimpleDateFormat

// Query:
// http://localhost:8890/sparql?default-graph-uri=&query=select+distinct+%3Fg+%3Fs+%3Fp+%3Fo+where+%7B+graph+%3Fg+%7B+%3Fs+%3Fp+%3Fo.+%3Fs+a+%3Chttp%3A%2F%2Fpurl.oclc.org%2FNET%2Fssnx%2Fssn%23SensingDevice%3E+%7D+%7D+LIMIT+100&format=text%2Fhtml&timeout=0&debug=on
// See https://jena.apache.org/tutorials/sparql_datasets.html
// select distinct ?g ?s ?p ?o where { graph ?g { ?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#SensingDevice> } } LIMIT 100
// select distinct ?g ?s ?p ?o where { graph ?g { ?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> } } LIMIT 100

// All observation values
// select distinct ?g ?s ?observationValue where { graph ?g { ?s <http://purl.oclc.org/NET/ssnx/ssn#hasValue> ?observationValue. ?s a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> } } LIMIT 100

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

      processSensor(sensorUri, it.@value, base);
    }
  }
  catch ( Exception e ) {
    println("ERROR...."+e.message);
  }
}

def processSensor(sensorUriString, sensorLocalId, sensorDataBaseUrl) {
  def type_map = [
    'PM2.5':[
             shortcode:'PM25',
             description:'Particulate matter that is 2.5 micrometers in diameter',
             uri:'http://en.wikipedia.org/wiki/Particulates'],
    'O3':[
             shortcode:'O3',
             description:'Ozone',
             uri:'http://en.wikipedia.org/wiki/Ozone'],
    'NO2':[
             shortcode:'NO2', 
             description:'Nitrogen Dioxide',
             uri:'http://en.wikipedia.org/wiki/NO2'],
    'Pressure':[
             shortcode:'Pressure', 
             description:'Pressure',
             uri:'http://en.wikipedia.org/wiki/Atmospheric_pressure'],
    'PM25':[
             // Ian:: Is this correct - PM25 is actually PM2.5?? thats what I've assumed
             shortcode:'PM25',
             description:'Particulate matter that is 2.5 micrometers or less in diameter',
             uri:'http://en.wikipedia.org/wiki/Particulates'],
    'PM10':[
             shortcode:'PM10',
             description:'Particulate matter that is 10 micrometers or less in diameter',
             uri:'http://en.wikipedia.org/wiki/Particulates'],
    'CO':[
             shortcode:'CO',
             description:'Carbon Monoxide',
             uri:'http://en.wikipedia.org/wiki/Carbon_monoxide'],
  ]



  try {
    println("Process Sensor....");

    // def graph = new VirtGraph(sensorUri, "jdbc:virtuoso://localhost:1111", "dba", "dba");
    // def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");
    def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");

    def sdf = new SimpleDateFormat('yyMMddhhmm')
    def reading_uri_format = new SimpleDateFormat('yyyyMMddhhmm')
    def reading_date_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
  
    Node class_sensing_device = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#SensingDevice');
    Node class_observation_value = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#ObservationValue');
    Node type_pred = Node.createURI('http://www.w3.org/1999/02/22-rdf-syntax-ns#type');
    Node measurement_property_pred = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#MeasurementProperty');
    Node dc_title_pred = Node.createURI('http://purl.org/metadata/dublin_core#Title');
    Node max_timestamp = Node.createURI('uri://opensheffield.org/properties#maxTimestamp');
    Node last_check = Node.createURI('uri://opensheffield.org/properties#lastCheck');
    Node has_value_pred = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#hasValue');
    Node end_time_pred = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#endTime');
    Node sensor_pred = Node.createURI('uri://opensheffield.org/properties#sensor');
  
    type_map.each { key,value ->
      // Create a uri for each of our measurement types
      measurement_type_uri = Node.createURI(value.uri);
      graph.add(new Triple(measurement_type_uri, type_pred, measurement_property_pred));
      graph.add(new Triple(measurement_type_uri, dc_title_pred, NodeFactory.createLiteral(value.description)));
    }
  
    // see https://www.google.co.uk/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&cad=rja&uact=8&ved=0CCcQFjAA&url=http%3A%2F%2Fwww.w3.org%2F2005%2FIncubator%2Fssn%2Fwiki%2Fimages%2F2%2F2e%2FSemanticSensorNetworkOntology.pdf&ei=AcexU6e1CY3sO8D6gPAM&usg=AFQjCNHrD8E9qlXEk_bU0kwuLNtNbVC5ng&bvm=bv.69837884,d.ZWU
    // See http://www.w3.org/2005/Incubator/ssn/ssnx/ssn
  
    // Fetch all entries since last_check
    def from="01/01/1900"
    def to="01/12/2014"
    def data_url_str = "${sensorDataBaseUrl}/cgi-bin/gifgraph_sheffield.cgi/data.txt?format=csv&zmacro=${sensorLocalId}&from=${from}&to=${to}"
    println("Attempting to fetch ${data_url_str}");
    def data_url = new URL(data_url_str);
    def line = null

    def identified_type = null;
    type_map.each {
      if ( identified_type == null && 
           sensorLocalId.toLowerCase().contains(it.key.toLowerCase()) ) {
        identified_type = it.value;
      }
    }
  
    if ( identified_type ) {
      Node sensorUri = Node.createURI(sensorUriString);
      graph.add(new Triple(sensorUri, type_pred, class_sensing_device));
      graph.add(new Triple(sensorUri, measurement_property_pred, Node.createURI(identified_type.uri)));
      graph.add(new Triple(sensorUri, max_timestamp, NodeFactory.createLiteral('0')));
      graph.add(new Triple(sensorUri, last_check, NodeFactory.createLiteral('0')));

      def process = false
      data_url.withReader { br ->
        while ( ( line = br.readLine() ) != null ) {
          if ( line.startsWith( 'EOF') )
            process=false;
       
          if ( process ) {
            try {
              cells = line.split(",");
              println("DATA: ${cells}");
              def date = sdf.parse(cells[0]+cells[1]);
              // def hour = cells[1].substring(0,2);
              // def min = cells[1].substring(2,4);
  
              int i=2;
              while ( i < cells.length ) {
                println("${date} \"${cells[i]}\" (out of ${cells.length})");
                if ( cells[i].trim().length() > 0 ) {
                  println("Publish.. ${reading_uri_format.format(date)} ${cells[i]}");
                  Node measurement_uri = Node.createURI(sensorUriString+'/'+reading_uri_format.format(date))
                  graph.add(new Triple(measurement_uri, type_pred, class_observation_value));
                  graph.add(new Triple(measurement_uri, has_value_pred, NodeFactory.createLiteral(cells[i].trim())));
                  graph.add(new Triple(measurement_uri, sensor_pred, sensorUri);
                  graph.add(new Triple(measurement_uri, end_time_pred, NodeFactory.createLiteral("${reading_date_format.format(date)}")));
                  // Reading was made by sensor ${sensorUri}
                  // Timestamp : date.getTime()
                  // Measurement : cells[i].trim()
                }
                i++
              }
  
            }
            catch ( Exception e ) {
              e.printStackTrace()
            }
          }
          else {
            println("HEADER...");
          }
  
          if ( line.startsWith('EOH') )
            process=true
  
        }
      }
    }
    else {
      println("Not processed - unable to identify measurement type for ${sensorLocalId}");
    }
  
    graph.close();
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
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
