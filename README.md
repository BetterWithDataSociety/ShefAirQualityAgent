

ShefAirQualityAgent
===================

Software agent to consume the raw sheffield air quality data and present it as open data


Notes on consuming the data

Station types

Current / Legacy

Reading Types

Raw / Ratified


Data files seem to be composed of a headers section listing the station, researching other values..

    #Sheffield Tinsley, NO2, 000[M], Value
    #Sheffield Tinsley, NO2, 000[M], Value
    ST3+
    1 M0003DIF 1 [ppb]
    2 M0003DIF 1 [ppb]
    EOH
    140623, 1900, 20.9084,    , 
    140623, 2000, 25.518,    , 
    140623, 2100, 26.0832,    , 
    140623, 2200, 20.425,    , 
    140623, 2300, 18.642,    , 
    140623, 2400, 21.7629,    , 




Listing all stations

Page at http://sheffieldairquality.gen2training.co.uk/sheffield/index.html seems to contain a HTML hotspot map listing all stations

    <map name="Sheffield">
      <area shape="rect" coords="110,65,175,77" href="Groundhog1/index.html" alt="Groundhog 1">
      <area shape="rect" coords="195,40,260,52" href="Groundhog2/index.html" alt="Groundhog 2">
      <area shape="rect" coords="60,160,135,172" href="Groundhog3/index.html" alt="Groundhog 3">
      <area shape="rect" coords="75,105,150,117" href="Groundhog4/index.html" alt="Groundhog 4">
      <area shape="rect" coords="140,125,210,138" href="Groundhog5/index.html" alt="Groundhog 5">
      <area shape="rect" coords="45,128,130,140" href="Sheffield_Centre/index.html" alt="Sheffield Centre">
      <area shape="rect" coords="192,65,274,78" href="Sheffield_Tinsley/index.html" alt="Sheffield Tinsley">
      <area shape="rect" coords="180,100,260,111" href="Weather_Mast/index.html" alt="Weather Mast">

      <area shape="rect" coords="117,41,137,61" href="Sheffield_Centre/index.html" alt="Sheffield Centre">
      <area shape="rect" coords="148,114,168,134" href="Corn_Exchange/index.html" alt="Corn Exchange">
      <area shape="rect" coords="235,88,255,108" href="Haslewood_Close/index.html" alt="KALLE">
    </map>

Easy to extract and parse, although extracting lat/lng for station not yet sorted

Each URL points to a frameset ( :( ) which contains a form and a data panel, the form lists the measurement types available at the station (I think)

.... History? ...




Extra notes

Set grapeConfig.xml in ~/.groovy...

  <ivysettings>
    <settings defaultResolver="downloadGrapes"/>
    <resolvers>
      <chain name="downloadGrapes">
        <filesystem name="cachedGrapes">
          <ivy pattern="${user.home}/.groovy/grapes/[organisation]/[module]/ivy-[revision].xml"/>
          <artifact pattern="${user.home}/.groovy/grapes/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]"/>
        </filesystem>
        
        <ibiblio name="codehaus" root="http://repository.codehaus.org/" m2compatible="true"/>
        <ibiblio name="snapshots.codehaus" root="http://snapshots.repository.codehaus.org/" m2compatible="true"/>
        <ibiblio name="apache" root="http://people.apache.org/repo/m2-ibiblio-rsync-repository/" m2compatible="true"/>
        <ibiblio name="apache-incubating" root="http://people.apache.org/repo/m2-incubating-repository/" m2compatible="true"/>
        <ibiblio name="maven" root="http://repo2.maven.org/maven2/" m2compatible="true"/>
      </chain>
    </resolvers>
  </ivysettings>



All properties of all sensing devices..

select distinct ?s ?p ?o where {?s ?p ?o. ?s a <http://purl.oclc.org/NET/ssnx/ssn#SensingDevice>} LIMIT 100


