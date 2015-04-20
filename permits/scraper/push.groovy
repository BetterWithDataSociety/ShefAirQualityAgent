@Grapes([
    @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
    @Grab(group='net.sf.opencsv', module='opencsv', version='2.0'),
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
import java.text.SimpleDateFormat;
import au.com.bytecode.opencsv.CSVReader
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype

// "permitReference","permitName","address","postcode","permitUrl","section","type","subtype"

def two_a_base_url='https://www.sheffield.gov.uk/environment/environmental-health/pollution/environmental-permitting/public-register/2a-processes.html';
def part_b_base_url='https://www.sheffield.gov.uk/environment/environmental-health/pollution/environmental-permitting/public-register/b-processes.html';

System.err.println("Run with \"groovy -Dgroovy.grape.autoDownload=false push.groovy\" to avoid startup lag");

println("Processing ${args[0]}");
CSVReader r = new CSVReader( new InputStreamReader(new FileInputStream(args[0]),java.nio.charset.Charset.forName('UTF-8')) )

String [] nl;

int rownum = 0;

// Read column headings
nl = r.readNext()
println("Column heads: ${nl}");

def graph = new VirtGraph('uri://opensheffield.org/datagrid/permits', "jdbc:virtuoso://localhost:1111", "dba", "dba");

Node class_sensing_device = Node.createURI('http://purl.oclc.org/NET/ssnx/ssn#SensingDevice');
Node class_industrial_permit = Node.createURI('uri://opensheffield.org/types#industrialPermit');
Node label_pred = Node.createURI('http://www.w3.org/2000/01/rdf-schema#label');
Node permit_document_pred = Node.createURI('uri://opensheffield.org/properties#permitDocumentURI');
Node permit_addr_pred = Node.createURI('uri://opensheffield.org/properties#permitAddressLabel');
Node permit_section_pred = Node.createURI('uri://opensheffield.org/properties#permitSection');
Node permit_type_pred = Node.createURI('uri://opensheffield.org/properties#permitType');
Node permit_subtype_pred = Node.createURI('uri://opensheffield.org/properties#permitSubType');
Node lat_pred = Node.createURI('http://www.w3.org/2003/01/geo/wgs84_pos#lat');
Node lon_pred = Node.createURI('http://www.w3.org/2003/01/geo/wgs84_pos#long');
Node type_pred = Node.createURI('http://www.w3.org/1999/02/22-rdf-syntax-ns#type');


// "permitReference","permitName","address","postcode","permitUrl","section","type","subtype"
while ((nl = r.readNext()) != null) {
  // println("row : ${nl}");
  if ( nl[3]?.length() > 0 ) {
    println("\n\n----------------------------");
    println("${nl[1]}");
    println("${nl[3]}");
    println("${nl[4]}")
    def geocoding = codepointOpenGeocode(nl[3].trim().replaceAll(' ',''));
    println(geocoding.response.geo.lat);
    println(geocoding.response.geo.lng);

    // Geocode via http://uk-postcodes.com/postcode/S119DE.json
    def permitUriString = "uri://opensheffield.org/datagrid/permits/scc/${nl[0]}"
    Node permitUri = Node.createURI(permitUriString);
    graph.add(new Triple(permitUri, type_pred, class_industrial_permit));
    graph.add(new Triple(permitUri, label_pred, Node.createLiteral(nl[1])));
    graph.add(new Triple(permitUri, permit_document_pred, Node.createLiteral(nl[4])));
    graph.add(new Triple(permitUri, lat_pred, Node.createLiteral(''+geocoding.response.geo.lat, XSDDatatype.XSDdouble)));
    graph.add(new Triple(permitUri, lon_pred, Node.createLiteral(''+geocoding.response.geo.lng, XSDDatatype.XSDdouble)));

    graph.add(new Triple(permitUri, permit_addr_pred, Node.createLiteral(nl[2])));
    graph.add(new Triple(permitUri, permit_section_pred, Node.createLiteral(nl[5])));
    graph.add(new Triple(permitUri, permit_type_pred, Node.createLiteral(nl[6])));
    if ( nl[7].length() > 0 ) {
      graph.add(new Triple(permitUri, permit_subtype_pred, Node.createLiteral(nl[7])));
    }
  }
  else {
    // println("No postcode for ${nl[0]}");
  }

}

graph.close();

def codepointOpenGeocode(postcode) {
  def result = null
  def http = new HTTPBuilder("http://uk-postcodes.com");
  http.request(Method.valueOf("GET"), ContentType.JSON) {
    uri.path = "/postcode/${postcode}.json"
    response.success = {resp, json ->
      result = [ address:postcode,
                   response: [
                     postcode: json.postcode,
                     geo: [
                       lat: "${json.geo.lat}",
                       lng: "${json.geo.lng}",
                       easting: "${json.geo.easting}",
                       northing: "${json.geo.northig}",
                       geohash: json.geo.geohash
                     ],
                     administrative: json.administrative
                   ],
                   lastSeen: System.currentTimeMillis(),
                   created: System.currentTimeMillis() ]
    }
    response.failure = { resp ->
      println("Not found: ${postcode}");
    }
  }
  result
}

