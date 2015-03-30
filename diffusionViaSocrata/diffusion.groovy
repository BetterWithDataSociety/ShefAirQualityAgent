@Grapes([
    // @GrabResolver(name='central', root='http://central.maven.org/maven2/'),
    @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
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

// http://www.sheffieldairmap.org/view_map.html
// http://uk-air.defra.gov.uk/networks/site-info?site_id=SHE2

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
import groovy.json.*

// Query:
// http://localhost:8890/sparql?default-graph-uri=&query=select+distinct+%3Fg+%3Fs+%3Fp+%3Fo+where+%7B+graph+%3Fg+%7B+%3Fs+%3Fp+%3Fo.+%3Fs+a+%3Chttp%3A%2F%2Fpurl.oclc.org%2FNET%2Fssnx%2Fssn%23SensingDevice%3E+%7D+%7D+LIMIT+100&format=text%2Fhtml&timeout=0&debug=on
// See https://jena.apache.org/tutorials/sparql_datasets.html
// select distinct ?g ?s ?p ?o where { graph ?g { ?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#SensingDevice> } } LIMIT 100
// select distinct ?g ?s ?p ?o where { graph ?g { ?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> } } LIMIT 100

// All observation values
// select distinct ?g ?s ?observationValue where { graph ?g { ?s <http://purl.oclc.org/NET/ssnx/ssn#hasValue> ?observationValue. ?s a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> } } LIMIT 100

// def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/index.html"
// def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/content.html"
println("Starting");


def store_uri = "jdbc:virtuoso://localhost:1111"

println("Populate..");
populate();

System.exit(0);


def populate() {
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
    Node sensor_id_property = Node.createURI('uri://opensheffield.org/properties#sensorId');
    Node sensor_platform_property = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#onPlatform');
    Node responsible_party_property = Node.createURI('uri://opensheffield.org/properties#responsibleParty');
    Node qa_property = Node.createURI('uri://opensheffield.org/properties#QACriteria');

    Node scci_epa_as_a_responsible_party = Node.createURI('https://www.sheffield.gov.uk/environment');

    def the_base_url = "https://data.sheffield.gov.uk/api/views/55pw-gvz4/rows.json?accessType=DOWNLOAD"
    def apiUrl = new URL(the_base_url);
    def diffusion_data_table_data = new JsonSlurper().parseText(apiUrl.text)

    diffusion_data_table_data.data.each {
      println(it);
    }
  }
  catch ( Exception e ) {
    println("ERROR...."+e.message);
  }

}

def processSensorStationFrameset(base,uri, name) {
  // Fetch page

  validateSensorStation(uri,name);

  try {
    def sensor_cluster_frameset = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(base+uri)
    sensor_cluster_frameset.FRAMESET.FRAME.each {
      if ( it.@name=='left' ) {
        println("Process sensor cluster at"+it.@src);
        processSensorStation(base,it.@src,name);
      }
    }
  }
  catch ( Exception e ) {
    println("ERROR....(${uri},${name})"+e.message);
  }
  // println(lhs)
}


def processSensorStation(base, uri, name) {
  println("processSensorStation(${base}, ${uri}, ${name})");
  try {
    def sensor_cluster = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(base+uri)
    println("Got sensor cluster page... find SELECT");
    sensor_cluster_select = sensor_cluster.depthFirst().SELECT.find { it.@name=='ic'}

    //validateSensorStation("", name)
    // def stationUri = "uri://opensheffield.org/datagrid/stations/${it.@value}"

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
             uri:'http://dbpedia.org/resource/PM_2.5'],
    'O3':[
             shortcode:'O3',
             description:'Ozone',
             uri:'http://dbpedia.org/resource/Ozone'],
    'NO2':[
             shortcode:'NO2', 
             description:'Nitrogen Dioxide',
             uri:'http://dbpedia.org/resource/NO2'],
    'SO2':[
             shortcode:'SO2', 
             description:'Sulphur Dioxide',
             uri:'http://dbpedia.org/resource/Sulfur_dioxide'],
    'Pressure':[
             shortcode:'Pressure', 
             description:'Pressure',
             uri:'http://dbpedia.org/resource/Atmospheric_pressure'],
    'PM25':[
             // Ian:: Is this correct - PM25 is actually PM2.5?? thats what I've assumed
             shortcode:'PM25',
             description:'Particulate matter that is 2.5 micrometers or less in diameter',
             uri:'http://dbpedia.org/resource/PM_2.5'],
    'PM10':[
             shortcode:'PM10',
             description:'Particulate matter that is 10 micrometers or less in diameter',
             uri:'http://dbpedia.org/resource/PM_10'],
    'CO':[
             shortcode:'CO',
             description:'Carbon Monoxide',
             uri:'http://dbpedia.org/resource/Carbon_monoxide'],
    'Weather_Mast.ic':[
             shortcode:'AT',
             description:'Ambient Temperature',
             uri:'http://dbpedia.org/resource/Outside_air_temperature'],
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
    Node sensor_id_property = Node.createURI('uri://opensheffield.org/properties#sensorId');
    Node sensor_platform_property = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#onPlatform');
    Node responsible_party_property = Node.createURI('uri://opensheffield.org/properties#responsibleParty');
    Node qa_property = Node.createURI('uri://opensheffield.org/properties#QACriteria');

    Node scci_epa_as_a_responsible_party = Node.createURI('https://www.sheffield.gov.uk/environment');
  
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
    // println("Attempting to fetch ${data_url_str}");
    def data_url = new URL(data_url_str);
    def line = null

    def identified_type = null;
    type_map.each {
      // println("Consider type ${it.key} in ${sensorLocalId}");
      if ( identified_type == null && sensorLocalId.toLowerCase().contains(it.key.toLowerCase()) ) {
        identified_type = it.value;
      }
    }
  
    if ( identified_type ) {
      Node sensorUri = Node.createURI(sensorUriString);
      graph.add(new Triple(sensorUri, type_pred, class_sensing_device));
      graph.add(new Triple(sensorUri, measurement_property_pred, Node.createURI(identified_type.uri)));
      graph.add(new Triple(sensorUri, sensor_id_property, Node.createLiteral(sensorLocalId)));
      graph.add(new Triple(sensorUri, sensor_platform_property, Node.createLiteral('scc_air_quality')));
      graph.add(new Triple(sensorUri, responsible_party_property, scci_epa_as_a_responsible_party));
      graph.add(new Triple(sensorUri, sensor_id_property, Node.createLiteral(sensorLocalId)));

      graph.remove(new Triple(sensorUri,max_timestamp,com.hp.hpl.jena.graph.Node.ANY));
      graph.remove(new Triple(sensorUri,last_check,com.hp.hpl.jena.graph.Node.ANY));
      graph.add(new Triple(sensorUri, max_timestamp, NodeFactory.createLiteral('0')));
      graph.add(new Triple(sensorUri, last_check, NodeFactory.createLiteral('0')));
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

def validateSensorStation(uri, name) {
  println("validateSensorStation ${uri}, ${name}");
}

def validateSensor(uri, name, type, cluster) {
  println("validateSensor ${uri}, ${name}, ${type}, ${cluster}");
  def result = null;
  result
}

def validateReading(sensorUri, date, time, value) {
  println("validateSensor ${sensorUri}, ${date}, ${time}, ${value}");
}

def processStaticProperties() {
  //
  // Firshill, Orphanage Road (groundhog1), - lat 53.402664,-1.463957
  // Tinsley Infants School (groundhog2), - 53.412365,-1.398938
  // Lowfield Junior and Infant School (groundhog3), - 53.364472,-1.471665
  // Wicker (groundhog4), 53.3871492,-1.4624831
  // Sheaf Square, opposite railway station (groundhog5), 53.3780314,-1.4649311
  // City Centre - Charter Square, 53.3784569,-1.4726883
  // Tinsley - Ingfield Avenue. 53.411002,-1.396479

  // http://www.w3.org/2003/01/geo/wgs84_pos#lat
  // http://www.w3.org/2003/01/geo/wgs84_pos#long
  try {
    def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");
    Node lat_pred = Node.createURI('http://www.w3.org/2003/01/geo/wgs84_pos#lat');
    Node lon_pred = Node.createURI('http://www.w3.org/2003/01/geo/wgs84_pos#long');
  
    def sensors = [
      [ uri:'uri://opensheffield.org/datagrid/sensors/Weather_Mast/Weather_Mast.ic',
        lat:'53.3932183',  // II: This is a very rough (Hand placed) guess based on SCC map
        lon:'-1.429708' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Tinsley/LD-Sheffield_Tinsley_NO2.ic',
        lat:'53.412365',
        lon:'-1.398938' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Tinsley/LD-Sheffield_Tinsley_CO.ic',
        lat:'53.412365',
        lon:'-1.398938' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Centre/LD-Sheffield_Centre_SO2_15min.ic',
        lat:'53.3784569',
        lon:'-1.4726883' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Centre/LD-Sheffield_Centre_PM25_Gravimetric.ic',
        lat:'53.3784569',
        lon:'-1.4726883' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Centre/LD-Sheffield_Centre_PM10_Gravimetric.ic',
        lat:'53.3784569',
        lon:'-1.4726883' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Centre/LD-Sheffield_Centre_O3.ic',
        lat:'53.3784569',
        lon:'-1.4726883' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Centre/LD-Sheffield_Centre_NO2.ic',
        lat:'53.3784569',
        lon:'-1.4726883' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Sheffield_Centre/LD-Sheffield_Centre_CO.ic',
        lat:'53.3784569',
        lon:'-1.4726883' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog5/LD-Groundhog5_PM10_Gravimetric.ic',
        lat:'53.3780314',
        lon:'-1.4649311' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog5/LD-Groundhog5_O3.ic',
        lat:'53.3780314',
        lon:'-1.4649311' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog5/LD-Groundhog5_NO2.ic',
        lat:'53.3780314',
        lon:'-1.4649311' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog4/LD-Groundhog4_PM10_Gravimetric.ic',
        lat:'53.3871492',
        lon:'-1.4624831' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog4/LD-Groundhog4_O3.ic',
        lat:'53.3871492',
        lon:'-1.4624831' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog4/LD-Groundhog4_NO2.ic',
        lat:'53.3871492',
        lon:'-1.4624831' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog3/LD-Groundhog3_SO2_15min.ic',
        lat:'53.364472',
        lon:'-1.471665' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog3/LD-Groundhog3_PM10_Gravimetric.ic',
        lat:'53.364472',
        lon:'-1.471665' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog3/LD-Groundhog3_NO2.ic',
        lat:'53.364472',
        lon:'-1.471665' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog2/LD-Groundhog2_SO2_15min.ic',
        lat:'53.412365',
        lon:'-1.398938' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog2/LD-Groundhog2_PM2.5.ic',
        lat:'53.412365',
        lon:'-1.398938' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog2/LD-Groundhog2_PM10_Gravimetric.ic',
        lat:'53.412365',
        lon:'-1.398938' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog2/LD-Groundhog2_NO2.ic',
        lat:'53.412365',
        lon:'-1.398938' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog1/LD-Groundhog1_SO2_15min.ic',
        lat:'53.402664',
        lon:'-1.463957' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog1/LD-Groundhog1_Pressure.ic',
        lat:'53.402664',
        lon:'-1.463957' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog1/LD-Groundhog1_PM10_Gravimetric.ic',
        lat:'53.402664',
        lon:'-1.463957' ],
      [ uri:'uri://opensheffield.org/datagrid/sensors/Groundhog1/LD-Groundhog1_NO2.ic',
        lat:'53.402664',
        lon:'-1.463957' ],
    ]

    sensors.each {
      Node sensorUri = Node.createURI(it.uri);
      graph.add(new Triple(sensorUri, lat_pred, Node.createLiteral(it.lat)));
      graph.add(new Triple(sensorUri, lon_pred, Node.createLiteral(it.lon)));
    }
  
    graph.close();
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
}
