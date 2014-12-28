@Grapes([
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
    // @Grab(group='virtuoso', module='virtjena', version='2'),
    // @Grab(group='virtuoso', module='virtjdbc', version='4.1')
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

System.err.println("Starting..");
System.err.println("Run with \"groovy -Dgroovy.grape.autoDownload=false getPermits.groovy\" to avoid startup lag");

System.out.print('"permitReference"');
System.out.print(',"permitName"');
System.out.print(',"address"');
System.out.print(',"permitUrl"');
System.out.print(',"section"');
System.out.print(',"type"');
System.out.println();

def two_a_base_url='https://www.sheffield.gov.uk/environment/environmental-health/pollution/environmental-permitting/public-register/2a-processes.html';

System.err.println "Loading page"
def parser = new org.cyberneko.html.parsers.SAXParser()

// Switch off namespaces
parser.setFeature("http://xml.org/sax/features/namespaces", false);

// Parse 2a page
def response_page = new XmlParser(parser).parse(two_a_base_url)
  

// First off, we want all the li elements that contain a child <a> tag with a URL that starts with
//   https://www.sheffield.gov.uk/dms/scc/management/corporate-communications/documents/environment/pollution/environmental-permitting/permits/2a
// The next part of the path
//   /non-ferrous/ATI-Permit/ATI%20Allvac%20Ltd.pdf
// Can be used to discern the "type" - non-ferrous in this case. Hopefully this structure will remain in place.

def permit_li_elements = response_page.depthFirst().findAll{it.name() == 'A' && it.@href.startsWith('/dms/scc/management/corporate-communications/documents/environment/pollution/environmental-permitting/permits/2a') }

permit_li_elements.each { permit_list_item ->

  def parent_list_item = permit_list_item.parent()
  System.err.println("Parent should be an li :: ${parent_list_item.name()}");
  def address = parent_list_item.P.text();
  def permit_name = permit_list_item.text();
  permit_name = permit_name.substring(0,permit_name.lastIndexOf('PDF')-1);

  System.err.println("${permit_name} ${address} ${permit_list_item.@href}");


  def permit_info = permit_list_item.@href.substring(113)
  def permit_info_substrings = permit_info.split('\\/')
  def permit_type = permit_info_substrings[0]
  def permit_holder_reference = permit_info_substrings[1].replaceAll('%20','_')
                .replaceAll('%28','_')
                .replaceAll('%29','_')
                .toLowerCase()
  def permit_ref = permit_info_substrings[2].replaceAll('.pdf','');
  permit_ref = permit_ref.replaceAll('%20','_').toLowerCase()

  def postcode_matches = address =~ /(GIR 0AA)|((([A-Z-[QVX]][0-9][0-9]?)|(([A-Z-[QVX]][A-Z-[IJZ]][0-9][0-9]?)|(([A-Z-[QVX]][0-9][A-HJKSTUW])|([A-Z-[QVX]][A-Z-[IJZ]][0-9][ABEHMNPRVWXY])))) [0-9][A-Z-[CIKMOV]]{2})/
  def postcode = ""
  if ( postcode_matches.size() > 0 ) {
    if ( postcode_matches[0].size() > 0 ) {
      postcode = postcode_matches[0][0]
    }
  }

  

  // println("Permit Type: ${permit_type}");

  outputLine((permit_holder_reference+'.'+permit_ref).trim(),
             permit_name.trim(),
             address.trim(), 
             postcode,
             'https://www.sheffield.gov.uk'+permit_list_item.@href.trim(), 
             'A(2)'.trim(), 
             permit_type.trim());
}


// permit_li_elements.each { permit_list_item ->
//   println("Processing -> ${permit_list_item.@href}");
// }

System.err.println("Finished");

def outputLine(permitReference,
               permitName,
               address,
               postcode,
               permitUrl,
               section,
               type) {
  System.out.print('"'+permitReference+'"');
  System.out.print(',"'+permitName+'"');
  System.out.print(',"'+address+'"');
  System.out.print(',"'+permitUrl+'"');
  System.out.print(',"'+section+'"');
  System.out.print(',"'+type+'"');
  System.out.println();
}
