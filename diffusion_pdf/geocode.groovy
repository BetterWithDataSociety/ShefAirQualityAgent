#!/usr/bin/env groovy

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
    @Grab(group='virtuoso', module='virtjdbc', version='4.1'),
    @Grab(group='de.alsclo', module='voronoi-java', version='1.0')
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
import groovyx.net.http.HTTPBuilder
import de.alsclo.voronoi.Voronoi
import de.alsclo.voronoi.graph.Point;

def cli = new CliBuilder(usage: 'measurements.groovy [-h] [-f file]')
// Create the list of options.
cli.with {
        h longOpt: 'help', 'Show usage information'
        f longOpt: 'file', args: 1, argName: 'file', 'Filename', required:true
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


println("Processing ${options.file}");
CSVReader r = new CSVReader( new InputStreamReader(new FileInputStream(options.file),java.nio.charset.Charset.forName('UTF-8')) )

String [] nl;
String [] out_header = [
  'area',
  'name',
  'easting',
  'northing',
  'latitude',
  'longitude',
  '2003',
  '2004',
  '2005',
  '2006',
  '2007',
  '2008',
  '2009',
  '2010',
  '2011',
  '2012',
  '2013',
  '2014',
  '2015',
  '2016'
];

int rownum = 0;

// Read column headings
nl = r.readNext()

def section=null;
def http = new HTTPBuilder( 'http://www.bgs.ac.uk' )

Collection<Point> points = [];

while (nl) {
  if ( nl[0] == 'TUBE' ) {
    println("${section} ${nl[1]} ${nl[2]} ${nl[3]}.");

    if ( ( nl[2] != null ) && ( nl[3] != null ) ) {
      // call http://www.bgs.ac.uk/data/webservices/CoordConvert_LL_BNG.cfc?method=BNGtoLatLng&easting=[six figure number]&northing=[six figure number]

      http.request( GET, JSON ) {
        uri.path = '/data/webservices/CoordConvert_LL_BNG.cfc'
        uri.query = [ method:'BNGtoLatLng', easting:nl[2],northing:nl[3] ]
 
        response.success = { resp, json ->
          // println "Query response: ${json}"
          def lon=json.LONGITUDE
          def lat=json.LATITUDE
          println("lat:${lat},lon:${lon}");

          def output_row = [
            section,
            nl[1],
            nl[2],
            nl[3],
            lat,
            lon
          ]

          int i=4;
          for ( ; i<nl.length; i++ ) {
            output_row.add(nl[i]);
          }

          println(output_row);
          points.add(new de.alsclo.voronoi.graph.Point(lat,lon));
        }
      }

    }
  }
  else {
    println("${nl}");
    section=nl[1]
  }
  nl = r.readNext()
}

// Compute voroni diagram
def diagram = new Voronoi(points);


println(". done");

