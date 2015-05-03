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
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.URLENC

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

println("Run as groovy -Dgroovy.grape.autoDownload=false  ./measurements.groovy\nTo avoid startup lag");


println("Starting..");
doStep1(args[0], args[1], args[2])
println("Done..");
System.exit(0);

def doStep1(token,un,pw) {
  // Query the store for all sensors on platform "scc_air_quality"
  try {
    def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");
    Node last_check = Node.createURI('uri://opensheffield.org/properties#lastCheck');
    Node max_timestamp = Node.createURI('uri://opensheffield.org/properties#maxTimestamp');

     // See https://jena.apache.org/documentation/query/app_api.html
    String queryString = 'SELECT ?sensor ?lastCheck ?sensorId ?maxTimestamp ' +
                         'WHERE { ' +
                         '   ?sensor <http://purl.oclc.org/NET/ssnx/ssn#onPlatform> "scc_air_quality". ' +
                         '   ?sensor <uri://opensheffield.org/properties#lastCheck> ?lastCheck. '+
                         '   ?sensor <uri://opensheffield.org/properties#sensorId> ?sensorId . ' +
                         '   OPTIONAL { ?sensor <uri://opensheffield.org/properties#maxTimestamp> ?maxTimestamp } . '+
                         '} ';
    Query sparql = QueryFactory.create(queryString);
    VirtuosoQueryExecution qExec = VirtuosoQueryExecutionFactory.create (sparql, graph);

    println("Running query..");
    try {
      ResultSet rs = qExec.execSelect();
      for ( ; rs.hasNext() ; ) {
        QuerySolution result = rs.nextSolution();
        RDFNode sensor = result.get("sensor");
        RDFNode lastCheck = result.get("lastCheck");
        RDFNode max_ts_value = result.get("maxTimestamp");
        RDFNode sensorId = result.get("sensorId");
        // System.out.println("Sensor:${sensor} lastCheck:${lastCheck} sensorId:${sensorId} maxTimestamp:${max_ts_value}");

        // lets try to upate lastcheck and set it to 1
        Node n = Node.createURI(sensor.getURI())

        // def resut_of_get_readings = getReadings(graph, n, lastCheck.toString(), max_ts_value.toString(), sensorId.toString());
        def resut_of_get_readings = getReadings(graph, n, lastCheck.toString(), max_ts_value, sensorId.toString(), token, un, pw);

        if ( resut_of_get_readings  ) {
          if ( resut_of_get_readings.largestTimestamp ) {
            // graph.remove(new Triple(n,last_check,com.hp.hpl.jena.graph.Node.ANY));
            graph.remove(new Triple(n,last_check,NodeFactory.createLiteral(lastCheck.toString())));
            graph.add(new Triple(n, last_check, NodeFactory.createLiteral("${System.currentTimeMillis()}".toString())));
            graph.remove(new Triple(n,max_timestamp,NodeFactory.createLiteral("0")));
            graph.remove(new Triple(n,max_timestamp,NodeFactory.createLiteral(max_ts_value.toString())));
            println("Set last timestamp to ${resut_of_get_readings.largestTimestamp}");
            graph.add(new Triple(n, max_timestamp, NodeFactory.createLiteral("${resut_of_get_readings.largestTimestamp}".toString())));
          }
        }
      }
    } finally {
      qExec.close();
    }
    graph.close();
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
  finally {
  }
}

def getReadings(graph, sensor_node, last_check, highest_timestamp, sensor_id, token, un, pw) {
  println("getReadings for ${sensor_id} since ${last_check} higest timestamp so far is ${highest_timestamp}");
  def num_readings = 0;
  def biggest_date = 0;
  try {
    Node class_sensing_device = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#SensingDevice');
    Node class_observation_value = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#ObservationValue');
    Node type_pred = Node.createURI('http://www.w3.org/1999/02/22-rdf-syntax-ns#type');
    Node measurement_property_pred = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#MeasurementProperty');
    Node dc_title_pred = Node.createURI('http://purl.org/metadata/dublin_core#Title');
    Node max_timestamp = Node.createURI('uri://opensheffield.org/properties#maxTimestamp');
    Node has_value_pred = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#hasValue');
    Node raw_value_pred = Node.createURI('uri://opensheffield.org/properties#rawValue');
    Node end_time_pred = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#endTime');
    Node sensor_pred = Node.createURI('uri://opensheffield.org/properties#sensor');

    def sdf = new SimpleDateFormat('yyMMddHHmm')
    def reading_uri_format = new SimpleDateFormat('yyyyMMDDHHmm')
    def reading_date_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");

    def to  = sdf.format(new Date(System.currentTimeMillis()+(1000*60*60*24)));

    // Take off a day - to get any 
    def int_start_date = new Date(Long.parseLong(highest_timestamp.toString()))

    def two_months_ago = new Date( System.currentTimeMillis() - ( 1000*60*60*24*60 ) )

    if ( int_start_date < two_months_ago ) {
      println("Rounding start date.. this should be commented out if doing a full re-population");
      int_start_date = two_months_ago
    }

    // If this is more than 2 months in the past, round it.
    def from  = sdf.format(int_start_date);
    
    // Add an hour on - we will get all the readings so far today that way
    def data_url_str = "http://sheffieldairquality.gen2training.co.uk/cgi-bin/gifgraph_sheffield.cgi/data.txt?format=csv&zmacro=${sensor_id}&from=${from}&to=${to}"
    // Formatted as yymmdd
    println("data url: ${data_url_str}");

    def data_url = new URL(data_url_str);
    def line = null
    def row = 0;
  
    def data_rows = []

    def process = false
    data_url.withReader { br ->
      println("Reading response lines, query was \n"+data_url_str+"\n\n");
      while ( ( line = br.readLine() ) != null ) {
        if ( line.startsWith( 'EOF') )
          process=false;

        if ( process ) {
          row++
          try {
              cells = line.split(",");
              // println("DATA: ${cells}");
              def date = sdf.parse(cells[0].trim()+cells[1].trim());
              println("${sensor_id} ${cells[0].trim()+cells[1].trim()} == ${date}");
              // def hour = cells[1].substring(0,2);
              // def min = cells[1].substring(2,4);
              def parsed_date = reading_uri_format.format(date)

              int i=2;
              while ( i < cells.length ) {
                if ( cells[i].trim().length() > 0 ) {
                  // println("Publish.. ${sensor_id} ${reading_uri_format.format(date)} ${cells[i]}");
                  Node measurement_uri = Node.createURI(sensor_node.toString()+'/'+parsed_date);
                  graph.add(new Triple(measurement_uri, type_pred, class_observation_value));
                  graph.add(new Triple(measurement_uri, raw_value_pred, NodeFactory.createLiteral(cells[i].trim(), XSDDatatype.XSDdouble)));
                  graph.add(new Triple(measurement_uri, has_value_pred, NodeFactory.createLiteral(cells[i].trim(), XSDDatatype.XSDdouble)));
                  graph.add(new Triple(measurement_uri, sensor_pred, sensor_node));
                  graph.add(new Triple(measurement_uri, end_time_pred, NodeFactory.createLiteral("${reading_date_format.format(date)}")));
                  num_readings++;

                  // Reading was made by sensor ${sensorUri}
                  // Timestamp : date.getTime()
                  // Measurement : cells[i].trim()

                  // Even if the reading has no data, it's still the biggest one we have seen.
                  // Not true because future readings have not been taken yet. Sucks!
                  if ( date.getTime() > biggest_date ) { 
                    biggest_date = date.getTime()
                  }

                  data_rows.add([measurement_uri, sensor_pred, end_time_pred, cells[i].trim()])
                }

                i++
              }

          }
          catch ( Exception e ) {
            e.printStackTrace()
          }
        }
        else {
        }

        if ( line.startsWith('EOH') ) {
          process=true
        }

        if ( ( row % 10000 ) == 0 ) {
          println("${row} rows, ${num_readings} observationsn for ${sensor_id} so far. Max timestamp: ${reading_uri_format.format(new Date(biggest_date))}");
          pushToSocrata(data_rows, token, un, pw);
          data_rows = []
        }
      }
    }

    pushToSocrata(data_rows, token, un, pw);
    println("Max timestamp for ${sensor_id} : ${reading_uri_format.format(new Date(biggest_date))} added ${num_readings} observations");
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }

  return [
    numObservationsAdded:num_readings,
    largestTimestamp:biggest_date
  ]
}


def pushToSocrata(data_rows, token, un, pw) {
  def colheads = "ssn_measurement_id,ssn_sensor_id,ssn_measurement_time,ssn_measurement_value"
  // data_rows.add([measurement_uri, sensor_pred, end_time_pred, cells[i].trim()])
  // https://data.sheffield.gov.uk/Environment/Live-Air-Quality-Data-Stream/mnz9-msrb/
  def http = new HTTPBuilder( 'https://data.sheffield.gov.uk' )

  def sw = new StringWriter()
  sw.write(colheads)
  sw.write('\n')
  data_rows.each{ row ->
    sw.write('"'+row[0]+'"');
    sw.write(',"'+row[1]+'"');
    sw.write(',"'+row[2]+'"');
    sw.write(',"'+row[3]+'"');
    sw.write('\n')
  }

  http.request( POST ) { req ->
    uri.path = '/Environment/Live-Air-Quality-Data-Stream/mnz9-msrb.json'
    send 'text/csv', sw.toString()
    headers.'X-App-Token' = token

    response.success = { resp ->
        println "POST response status: ${resp.statusLine}"
        // assert resp.statusLine.statusCode == 201
    }
  }
}
