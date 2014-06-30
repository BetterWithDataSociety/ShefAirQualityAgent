@Grapes([
    @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
    @Grab(group='xerces', module='xercesImpl', version='2.9.1') ])

import groovyx.net.http.*
import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import static groovy.json.JsonOutput.*

def the_base_url = "http://www.sheffieldhelpyourself.org.uk/"

// HYS is returning CP 1252 pages - need to fix this somehow
// groovyx.net.http.ParserRegistry.setDefaultCharset('cp1252')

// This is a bit grotty, but the DB isn't big enough to worry about, so cram it into memory whilst we reconstruct
// the full set of subject headings we find against each record (They aren'd displayed so we need to x-ref the urls where we find pages)
def rec_map = [:]
def keyword_map = [:]

// This is the process-everything method
processTopLevel(the_base_url, rec_map, keyword_map);


// Use this approach to test individual records
//
// def tst = [:]
// processRecord(tst,'25602');
// processRecord(tst,'24818');
// processRecord(tst,'23947');
// println prettyPrint(toJson(tst))

rec_map.each { key, value ->
  println("id: ${key}, keywords:${value.keywords}");
}

println("${rec_map.size()} Resources");
println("${keyword_map.size()} Keywords");

try{
  def out= new ObjectOutputStream(new FileOutputStream('serializedMapsOfHYSData.obj'))
  out.writeObject(rec_map)
  out.close()
}finally{}



def processTopLevel(base_url, rec_map, keyword_map) {

  
  println "Loading page"
  def response_page = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(base_url)
  println "Done.. Parse"
  
  // <a href="sport.asp"><img src="images/SportButton.jpg" alt="Sport image" border="0" />
  // def select_element = response_page.depthFirst().A.findAll()
  def select_element = response_page.depthFirst().A.findAll { it.'@class'=='title5' }

  println("Got elements");

  select_element.each { se ->
    processTopLevelHeading(se.'@href', base_url, rec_map, keyword_map);
  }

  println("Done");
}

def processTopLevelHeading(heading, base_url, rec_map, keyword_map) {
  try {
    println("Processing ${heading}");
    def response_page = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse(base_url+heading)

    // Extract all links of the form http://www.sheffieldhelpyourself.org.uk/keyword_search.asp?keyword=ABSEILING

    def keyword_links =  response_page.depthFirst().A.findAll { it.'@href'?.contains('keyword_search.asp?keyword=')}

    keyword_links.each { kwl ->
      def keyword_url = kwl.'@href'
      def eq_pos = keyword_url.lastIndexOf('=')+1;
      def keyword=keyword_url.substring(eq_pos, keyword_url.length())
      println('Keyword: '+keyword);
      if ( keyword_map[keyword]==null ) {
        keyword_map[keyword] = [:]
      }
      processKeywordSearch(heading,keyword, rec_map, keyword_map);
    }

    //http://www.sheffieldhelpyourself.org.uk/welfare_search.asp?code1=HY/GAY
    def welfare_search_links = response_page.depthFirst().A.findAll { it.'@href'?.contains('welfare_search.asp?code1=')}

    welfare_search_links.each { kwl ->
      def keyword_url = kwl.'@href'
      def eq_pos = keyword_url.lastIndexOf('=')+1;
      def keyword=keyword_url.substring(eq_pos, keyword_url.length())
      println('WelfareSearchCode: '+kwl.'@href')
    }

  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
}

def processKeywordSearch(heading, keyword, rec_map, keyword_map) {
  println("Processing search using http://www.sheffieldhelpyourself.org.uk/keyword_search.asp?keyword=${keyword}");
  try {
    def response_page = new XmlParser( new org.cyberneko.html.parsers.SAXParser() ).parse("http://www.sheffieldhelpyourself.org.uk/keyword_search.asp?keyword=${keyword}")

    // http://www.sheffieldhelpyourself.org.uk/full_search_new.asp?group=23453
    def records = response_page.depthFirst().A.findAll { it.'@href'?.contains('full_search_new.asp?group=') }
    def clean_kw = clean(keyword);
    def clean_cat = clean(heading?.replaceAll('.asp','').replaceAll('.html',''));

    if ( records != null ) {
      records.each { rec ->
        record_url = rec.'@href'
        record_eq_pos = record_url.lastIndexOf('=')+1
        record_id = record_url.substring(record_eq_pos,record_url.length());
        println("Record : ${record_id} seen under heading ${heading} in keyword ${keyword}");
  
        if ( rec_map[record_id] == null ) {
          rec_map[record_id] = [:]
          rec_map[record_id].id = 'SHYS'+record_id
          rec_map[record_id].keywords = [clean_kw]
          rec_map[record_id].categories = [clean_cat]
          processRecord(rec_map[record_id], record_id)
        }
        else {
          if ( !rec_map[record_id].keywords.contains(clean_kw) ) {
            rec_map[record_id].keywords.add(clean_kw);
          }
          if ( !rec_map[record_id].categories.contains(clean_cat) ) {
            rec_map[record_id].categories.add(clean_cat);
          }
        }
      }
    }
    else {
      println("NO records found for ${keyword}");
    }
  }
  catch ( Exception e ) {
    e.printStackTrace();
  }
}

def processRecord(rec, record_id) {

  println("Process record ${record_id}");



  try {

    def parser = new org.cyberneko.html.parsers.SAXParser()
    def charset = "Windows-1252"  // The encoding of the page 
    parser.setProperty("http://cyberneko.org/html/properties/default-encoding", charset) 
    parser.setFeature("http://cyberneko.org/html/features/scanner/ignore-specified-charset", true)    // Forces the parser to use the charset we provided to him. 

    def response_page = new XmlParser( parser ).parse("http://www.sheffieldhelpyourself.org.uk/full_search_new.asp?group=${record_id}")
 
    def details_div = response_page.BODY.DIV.findAll { it.'@align'='left' }

    // Summary
    // The details page is split up by table rows, the details table below seems to correctly identify everything which
    // relates to a record
    if ( details_div.size() == 1 ) {
      def details_table = details_div[0].TABLE.TBODY

      // details_table is split into rows, each row can contain 0,1 or more items of information which broadly fall into four classes
      // 1) Title and Previous title - <tr><td><font><b>Title
      // 2) Description - <tr><td><font>text,sub-html
      // 3) Empty <tr><td> not sure if this is for visual spacing, or missing info!
      // 4) General info <tr><td><table><tbody><tr><td> Followed by sequence of elements with property identified in <font><b>Address: style elements

      details_table.TR.each { table_row ->
        processRow(table_row, rec)
      }

      println("rec: ${rec}");
    }
  }
  catch ( Exception e ) {
    e.printStackTrace()
  }
}

def processRow(row, rec) {
  println("Process row");
  // Try to figure out what we have
  if ( row.TD.FONT.B.size() == 1 ) {
    // title or previous title
    // println("Title or Previous title ${row.TD.FONT.B.text()}");
    addPropValue(rec,'title',row.TD.FONT.B.text());
  }
  if ( row.TD.FONT.A.size() == 1 ) {
    addPropValue(rec,'url',row.TD.FONT.A.'@href'.text());
  }
  if ( row.TD.FONT.STRONG.size() == 1 ) {
    // title or previous title or Charity Number
    println("Strong element ${row.TD.FONT.STRONG.text()}");
    propname = row.TD.FONT.STRONG.text().replaceAll(':','').replaceAll(' ','')
    addPropValue(rec,propname,row.TD.FONT.text());
  }
  else if ( row.TD.FONT.size() == 1 ) {
    // descriptive element
    // println("description ${row.TD.FONT.text()}");
    addPropValue(rec,'description',row.TD.FONT.text());
  }
  else if ( row.TD.TABLE.size() == 1 ) {
    // Subsection
    processSubsection(rec, row.TD.TABLE)
  }
  else {
    println("unhandled: ${row}");
  }
}

def processSubsection(rec, tab) {
  println("\n\nProcessing subsection...");
  def current_property = null
  tab.TBODY.TR.TD[0]?.children().each { ae ->
    println("  Processing td");

    if ( ae.name() == 'FONT' ) {
      if ( ae.B.size() > 0 ) {
        println("Got a B element -${ae.B.text()}- it names a property");
        switch ( ae.B.text() ) {
          case 'Address:':
            current_property='address'
            break;
          case 'Contact Name:':
            current_property='contact'
            break;
          case 'Days and Times:':
            current_property='daysAndTimes'
            break;
          case 'Disabled Access Details:':
            current_property='access'
            break;
          case 'Email:':
            current_property='email'
            break;
          case 'Fax:':
            current_property='fax'
            break;
          case 'Further Access Details:':
            current_property='access'
            break;
          case 'Minicom:':
            current_property='minicom'
            break;
          case 'Mobile:':
            current_property='mobile'
            break;
          case 'Telephone Details:':
          case 'Telephone 2 Details:':
          case 'Telephone 3 Details:':
            current_property='telephoneDetails'
            break;
          case 'Telephone:':
          case 'Telephone 2:':
          case 'Telephone 3:':
            current_property='telephone'
            break;
          case 'Service/Activity Details:':
            println("ServiceActivityDetails......");
            def subrec = [:]
            if ( rec['timesAndPlaces'] == null )
              rec['timesAndPlaces'] = []
            rec['timesAndPlaces'].add(subrec)
            rec = subrec
            current_property='address'
            break;
          default:
            println("Unknown B element: ${ae.B.text()}");
            break;
        }
      }
      else if ( ae.A.size() == 1 ) {
        addPropValue(rec,'postcode',ae.A.text())
        addPropValue(rec,'maplink',ae.A.'@href'.text())
      }

      if ( ( current_property != null ) && ( ae.text().length() > 0 ) ) {
        addPropValue(rec, current_property,ae.text());
      }
    }
    else {
      println("Unknown ${ae.name()}");
    }

  }
}

def addPropValue(rec, property, value) {
  println("addPropValue('${property}','${value}')");
  if ( rec[property] == null ) {
    rec[property] = []
  }

  if ( ( value != null ) &&
       ( value.trim().length() > 0 ) &&
       ( isNotStop(value) ) ) {
    if ( ( property=='keywords') || ( property=='access' ) ) {
      def clean_kw = clean(value.trim())
      if ( !rec[property].contains(clean_kw) ) {
        println("Adding clean ${property} ${clean_kw}");
        rec[property].add(clean_kw);
      }
    }
    else {
      rec[property].add(value)
    }
  }
}

def isNotStop(v) {
  def result=true
  if ( v.endsWith('(View Map)') ) {
    result = false
  }
  result
}

def clean(s) {
  s.replaceAll('\\+',' ').replaceAll(';',' ').toLowerCase().split(' ').collect{ it.capitalize() }.join(' ').trim()
}
