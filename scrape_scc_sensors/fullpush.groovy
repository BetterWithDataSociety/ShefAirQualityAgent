#!/usr/bin/env groovy

@GrabResolver(name='central', root='http://central.maven.org/maven2/')
@Grapes([
    @Grab(group='org.slf4j', module='slf4j-api', version='1.7.6'),
    @Grab(group='org.slf4j', module='jcl-over-slf4j', version='1.7.6'),
    @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
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
import org.apache.http.*
import org.apache.http.protocol.*
import org.codehaus.groovy.runtime.MethodClosure




/** Query:
 *
 *  http://localhost:8890/sparql?default-graph-uri=&query=select+distinct+%3Fg+%3Fs+%3Fp+%3Fo+where+%7B+graph+%3Fg+%7B+%3Fs+%3Fp+%3Fo.+%3Fs+a+%3Chttp%3A%2F%2Fpurl.oclc.org%2FNET%2Fssnx%2Fssn%23SensingDevice%3E+%7D+%7D+LIMIT+100&format=text%2Fhtml&timeout=0&debug=on
 *
 * See https://jena.apache.org/tutorials/sparql_datasets.html
 * select distinct ?g ?s ?p ?o where { graph ?g { ?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#SensingDevice> } } LIMIT 100
 * select distinct ?g ?s ?p ?o where { graph ?g { ?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> } } LIMIT 100

 * All observation values
 * select distinct ?g ?s ?observationValue where { graph ?g { ?s <http://purl.oclc.org/NET/ssnx/ssn#hasValue> ?observationValue. ?s a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> } } LIMIT 100
 *
 * Different base URLs over time
 * def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/index.html"
 * def the_base_url = "http://sheffieldairquality.gen2training.co.uk/sheffield/content.html"
 */


def cli = new CliBuilder(usage: 'measurements.groovy [-h] [-t socrata_token] [-u socrata_user] [-p socrata_password] [-d]')
// Create the list of options.
cli.with {
        h longOpt: 'help', 'Show usage information'
        t longOpt: 'token', args: 1, argName: 'token', 'Socrata Token', required:false
        u longOpt: 'user', args: 1, argName: 'user', 'Socrata Username', required:false
        p longOpt: 'password', args: 1, argName: 'password', 'Socrata Password', required:false
        d longOpt: 'delta', args: 0, argName: 'delta', 'Only process a delta, rather than the full import', required:false
}

def options = cli.parse(args)
if (!options) {
  println("No options");
  return
}
else {
  println(options)
}

// Show usage text when -h or --help option is used.
if (options.h) {
  cli.usage()
  return
}

doStep1(options.token, options.user, options.password, options.d ? true : false);

println("Done..");
System.exit(0);

def doStep1(token,un,pw,delta_only) {

  println("Step1(token:${token},un:${un},pw:${pw},delta:${delta_only}");

  // Query the store for all sensors on platform "scc_air_quality"
  try {
    def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");
    Node last_check = Node.createURI('uri://opensheffield.org/properties#lastCheck');
    Node max_timestamp = Node.createURI('uri://opensheffield.org/properties#maxTimestamp');

     // See https://jena.apache.org/documentation/query/app_api.html
    String queryString = '''select ?mesaurementuri ?sensor ?observationTime ?observationValue where {
      graph ?g {
        ?mesaurementuri a <http://purl.oclc.org/NET/ssnx/ssn#ObservationValue> .
        ?mesaurementuri <http://purl.oclc.org/NET/ssnx/ssn#endTime> ?observationTime.
        ?mesaurementuri <http://purl.oclc.org/NET/ssnx/ssn#hasValue> ?observationValue .
        ?mesaurementuri <uri://opensheffield.org/properties#sensor> ?sensor .
      }
    } '''

    def socrata_date_format = new SimpleDateFormat("MM-dd-yyyy hh:mm a");

    Query sparql = QueryFactory.create(queryString);
    VirtuosoQueryExecution qExec = VirtuosoQueryExecutionFactory.create (sparql, graph);

    def data_rows = []
    def rowcount = 0;

    println("Running query..");
    try {
      ResultSet rs = qExec.execSelect();
      for ( ; rs.hasNext() ; ) {
        QuerySolution result = rs.nextSolution();
        RDFNode sensor = result.get("sensor");
        RDFNode observation_uri = result.get("mesaurementuri");
        RDFNode observationTime = result.get("observationTime");
        RDFNode observationValue = result.get("observationValue");
        def row_to_add = [observation_uri.toString(), sensor.toString(), observationTime.toString() , observationValue.asLiteral().getDouble()]
        data_rows.add(row_to_add);
        rowcount++

        if ( ( rowcount % 10000 ) == 0 ) {
          if ( ( token != null ) && ( data_rows.size() > 0 ) ) {
            pushToSocrata(data_rows, token, un, pw);
          }
          data_rows = []
          rowcount=0;
        }
      }

      if ( data_rows.size() > 0 ) {
        pushToSocrata(data_rows, token, un, pw);
      }

    } catch ( Exception e ) {
      e.printStackTrace();
    } finally {
      println("Query completed");
      qExec.close();
    }
    graph.close();
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
  finally {
    println("Done");
  }
}

def pushToSocrata(data_rows, token, un, pw) {


  if ( data_rows == null || data_rows.size() == 0 )
    return;

  println("\n\nPushing to socrata [${token},${un},${pw}] - ${data_rows.size()} rows\n\n");

  try {
  
    def colheads = "ssn_measurement_id,ssn_sensor_id,ssn_measurement_time,ssn_measurement_value"
    // data_rows.add([observation_uri, sensor_pred, end_time_pred, cells[i].trim()])
    // https://data.sheffield.gov.uk/Environment/Live-Air-Quality-Data-Stream/mnz9-msrb/
    // def http = new HTTPBuilder( 'https://data.sheffield.gov.uk' )
    def http = new HTTPBuilder( 'https://data.sheffield.gov.uk' )

    // Always handle text/csv as text/plain
    // http.parser.'text/csv' = http.parser.'text/plain'
    // http.encoder.putAt('text/csv', new MethodClosure(EncoderRegistry, 'encodeText'))
    http.encoder.putAt('text/csv', new MethodClosure(http.encoder, 'encodeText'))


  
    def sw = new StringWriter()
    sw.write(colheads)
    data_rows.each{ row ->
      sw.write('\n"'+row[0]+'"');
      sw.write(',"'+row[1]+'"');
      sw.write(',"'+row[2]+'"');
      sw.write(','+row[3]+'');
    }
    sw.write('\n');
  
    def content = sw.toString()
  
   // Add preemtive auth
   // http.client.addRequestInterceptor( new HttpRequestInterceptor() {
   //  void process(HttpRequest httpRequest, HttpContext httpContext) {
   //    String auth = "${un}:${pw}"
   //    String enc_auth = auth.bytes.encodeBase64().toString()
   //      httpRequest.addHeader('Authorization', 'Basic ' + enc_auth);
   //    }
   //  })

  
    def auth_str = "${un}:${pw}".bytes.encodeBase64().toString()

    println("Auth header: ${auth_str}");

    // https://dev.socrata.com/publishers/upsert.html
    // https://dev.socrata.com/blog/2015/01/11/soda-sensor-push.html
  
    http.request( POST ) { req ->
      // uri.path = '/Environment/Live-Air-Quality-Data-Stream/mnz9-msrb.json'
      uri.path = '/resource/mnz9-msrb.json'
      // uri.path = '/resource/vy52-yr3x.json'     // This is the pending dataset
      // uri.path = '/resource/je7y-4vsq.json'  // This is the published dataset
      query:['method':'append']
      headers.'Authorization' = "Basic ${auth_str}".toString()
      headers.'X-App-Token' = token
      requestContentType = 'text/csv'
      // send 'text/csv',  content
      body = content
  
      response.success = { resp, json ->
          println "POST response status: ${resp.statusLine}"
          println json
          // assert resp.statusLine.statusCode == 201
      }
  
      response.failure = { resp ->
          println("\n\n****Failure: line:${resp.statusLine} code:${resp.status} fullresp:${resp}\n\n");
      }
    }
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }

  println("Push to socrata completed");
}
