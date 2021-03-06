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

println("Starting..");
doStep1()
println("Done..");

def doStep1() {
  // Query the store for all sensors on platform "scc_air_quality"
  try {
    def graph = new VirtGraph('uri://opensheffield.org/datagrid/sensors', "jdbc:virtuoso://localhost:1111", "dba", "dba");
    Node last_check = Node.createURI('uri://opensheffield.org/properties#lastCheck');
    Node max_timestamp = Node.createURI('uri://opensheffield.org/properties#maxTimestamp');

1414395121067

        Node n = Node.createURI("uri://opensheffield.org/datagrid/sensors/Groundhog3/LD-Groundhog3_NO2.ic");
 
          graph.remove(new Triple(n,last_check,NodeFactory.createLiteral("1414395121067")));
        //   graph.add(new Triple(n, max_timestamp, NodeFactory.createLiteral("0".toString())));
    graph.close();
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
}
