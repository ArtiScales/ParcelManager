# Parcel Manager

Library providing functions, algorithms and workflows to process various parcel reshaping operations.
There are multiple ways of using this library as shown in the schema bellow. 
<ul>
    <li>Use built-in functions for diverse parcel management tasks</li>
    <li>Divide a single or few parcels: choose one of the multiple implemented <b>processes</b>. Only <b>marked</b> parcels will be divided</li>
    <li>Process various operations on a zone: use a predesigned <b>workflow</b> that runs various processes such as parcel division, parcel agregation, or other (attribute attribution, sizes thresholds, etc.). Workflow description can be found in <i>Colomb et al 2021</i>.</li>
    <li>On a community or on multiple communities: create advanced <b>scenarios</b> using simple .json files (<a href = "https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">see documentation here</a>).</li>
    <li>To run predefined workflow experiments, such as <b>densification studies</b> or <b>parcel evolution studies</b>, run existing use cases</li>
</ul>
<br />
Attributes are now only adapted for a french context but implementation of new country's nomenclature are possible. Please refer to this <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/resources/doc/AttributePolicy.md">parcel attribute and marking documentation</a>.  
<br />
This model has been developped during a post-doc contract for the <a href="http://thema.univ-fcomte.fr/">ThéMA laboratory</a> of the university of Franche-Comté. 
<br />
Still under active developpement.


## Technical requirements

Library based on the function from the library <a href = "https://github.com/ArtiScales/ArtiScales-tools">ArtiScales-tools</a> (no stable version yet) and on the GeoTools 23.0 library.
JavaDoc is available <a href="https://artiscales.github.io/javadoc/ParcelManager/">here</a> and more can be found on a coming article and on the <a href = "https://www.theses.fr/2019PESC2070">PhD thesis of Maxime Colomb</a>.

A graphical user interface is available in <a href="https://framagit.org/artiscales/parcelmanagergui">this repository</a> 


## Description of packages

<ul>
<li><b>analysis</b>: Contains automated analysis tools to analyze urban fabrics and produce tables and graphs.</li>
<li><b>decomposition</b>: Contains the different <b>processes</b> of decomposition algorithms.</li>
<li><b>fields</b>: Contains methods and tools to automatically set feature informations on the generated parcels. Solutions for french parcels types and ArtiScales parcels types are implemented.</li>
<li><b>workflow</b>: Contains the different <b>workflows</b> for the parcel manager process. The three main workflows that are now implemented are: 
    <ul>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/workflow/ZoneDivision.java"><i>ZoneDivision</i></a>: Run a parcel division algorithm on an input zone</li>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/workflow/ConsolidationDivision.java"><i>ConsolidationDivision</i></a>: Merges predesigned parcel together and run a decomposition task</li>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/workflow/Densification.java"><i>Densification</i></a>: Run the densification process on the predesigned parcels</li>
    </ul>
</li>
<li><b>parcelFunction</b>: Various functions for single parcels, sort by type (collection, state, schemas, attribute, marking, etc.).</li>
<li><b>scenario</b>: Contains objects used for processing an automated simulation on a large zone (<a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">technical description can be found here</a>).</li>
<li><b>workflow</b>: Cerate automated workflows for Parcel Manager which apply different workflows on specified zones</li>
</ul>
<br />
<div style="text-align:center">
<img src="misc/schema.png" alt="drawing" width="900" position="middle"/>
</div>
