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
import au.com.bytecode.opencsv.CSVWriter
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype
import groovyx.net.http.HTTPBuilder
import de.alsclo.voronoi.Voronoi
import de.alsclo.voronoi.graph.Point;

// ./geocode.groovy -f ./sheffield_diffusion_to_2016.csv -o sheffield_diffusion_to_2016_geocoded.csv

def cli = new CliBuilder(usage: 'measurements.groovy [-h] [-f file]')
// Create the list of options.
cli.with {
        h longOpt: 'help', 'Show usage information'
        f longOpt: 'file', args: 1, argName: 'file', 'Input Filename', required:true
        o longOpt: 'outfile', args: 1, argName: 'outfile', 'Output Filename', required:false
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

double max_lat=-1000;
double most_westerly_lon=-1000;
double min_lat=1000;
double most_easterly_lon=1000;

// A map of tubes indexed by northing and easting, a location may have > 1 tube
def tubes_by_location = [:]

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
          // println("lat:${lat},lon:${lon}");

          def output_row = [
            section:section,
            name:nl[1],
            easting:nl[2],
            northing:nl[3],
            lat:lat,
            lon:lon,
            data:[]
          ]

          int i=4;
          for ( ; i<nl.length; i++ ) {
            output_row.data.add(nl[i]);
          }

          if ( lat > max_lat ) max_lat = lat;
          if ( lon > most_westerly_lon ) most_westerly_lon = lon;
          if ( lat < min_lat ) min_lat = lat;
          if ( lon < most_easterly_lon ) most_easterly_lon = lon;

          // println(output_row);
          // Remember latitude = Y, Lon=X
          points.add(new de.alsclo.voronoi.graph.Point(lon,lat));

          tubes_by_location_key = lat+','+lon
          if ( tubes_by_location[tubes_by_location_key] == null ) {
            tubes_by_location[tubes_by_location_key] = [location:tubes_by_location_key, tube_data:[]]
          }
          tubes_by_location[tubes_by_location_key].tube_data.add(output_row);
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

println("After processing max_lat=${max_lat} most_westerly_lon=${most_westerly_lon} min_lat=${min_lat} most_easterly_lon=$most_easterly_lon{}");

// Compute voroni diagram
def voronoi = new Voronoi(points);

double bb_x = most_easterly_lon-0.01d // Max(neg lon) == most easterly point (?)
double bb_y = min_lat-0.01d 
double bb_width = most_westerly_lon-most_easterly_lon+0.02d
double bb_height = max_lat-min_lat+0.02d

println("applyBoundingBox(x:${bb_x},y:${bb_y},w:${bb_width},h:${bb_height}) -- x2=${bb_x+bb_width} y2=${bb_y+bb_height}");


def bound_voronoi = voronoi  // Because applyBoundingBox throws not implemented...
// def bound_voronoi = voronoi.applyBoundingBox(bb_x,bb_y,bb_width,bb_height);

def graph =  bound_voronoi.getGraph();
// Mix java8 lambdas and groovy closures ;) the Point toString method uses f2.2 as a patter, but the full double value
// is still available. What we need to do here is to add each edge to the 2 sites that it separates

// graph.edgeStream().forEach( { println( "s1:${it.getSite1()} s2:${it.getSite2()} p1:${it.getA()} p2:${it.getB()}" ) } );

if ( options.outfile ) {
  CSVWriter w = new CSVWriter( new OutputStreamWriter(new FileOutputStream(options.outfile),java.nio.charset.Charset.forName('UTF-8')) )
  String[] colheads = [ 'dt_group', 'dt_name', 'dt_easting', 'dt_northing', 'dt_lat', 'dt_lon', 
                      'dt_2003','dt_2004','dt_2005','dt_2006','dt_2007','dt_2008','dt_2009','dt_2010','dt_2011','dt_2012','dt_2013','dt_2014','dt_2015','dt_2016']
                       
  w.writeNext(colheads);

  tubes_by_location.each { key, tubeloc ->
    println(tubeloc.location);
    tubeloc.tube_data.each { tube ->
      println(tube.name);
      def row = [ tube.section, tube.name, tube.easting, tube.northing, tube.lat, tube.lon ]
      row.addAll(tube.data);

      String[] row_data = row.collect { it.toString() } .toArray(new String[row.size()])
      w.writeNext(row_data);
    }
  }
  w.close()
}


println(". done");


