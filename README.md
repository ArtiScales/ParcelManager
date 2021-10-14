# Parcel Manager

The library provides functions, algorithms and workflows that enable users to process various parcel reshaping operations.
This library can be used for:
<ul>
    <li>dividing a single parcel or a small set of parcels: choose one of the implemented <b>processes</b>. Only <b>marked</b> parcels will be divided</li>
    <li>apppling predesigned <b>workflows</b> on a zone or a set of zones. Workflows automatically run a series of processes such as parcel division, parcel aggregation, attribute changes, etc.
    <li>creating <b>scenarios</b> that apply to a whole community or a whole urban region. Scenarios are described in .json files (<a href = "https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">see documentation here</a>).</li>
    <li>applying predefined use cases</li>
</ul>

<br />
Parcel Manager has been developped during a post-doctoral research contract at <a href="http://thema.univ-fcomte.fr/">ThéMA laboratory</a> (Besançon, France) in the frame of the PubPrivLands project - I-site Bourgogne Franche-Comté. 
<br />

<br />


## Technical requirements

Parcel Manger involves functions from the library <a href = "https://github.com/ArtiScales/ArtiScales-tools">ArtiScales-tools</a> and the GeoTools library.
JavaDoc is available <a href="https://artiscales.github.io/javadoc/ParcelManager/">here</a>.

A graphical user interface is available in <a href="https://framagit.org/artiscales/parcelmanagergui">this repository</a> 


## Description of packages

<ul>
<li><b>analysis</b>: contains automated tools to analyze urban fabrics under the form of tables and graphs.</li>
<li><b>division</b>: contains the different <b>processes</b> of parcel division.</li>
<li><b>fields</b>: contains methods and tools to automatically set feature informations on the generated parcels.</li>
<li><b>workflow</b>: contains the <b>workflows</b>. Three main workflows are now implemented: 
    <ul>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/workflow/ZoneDivision.java"><i>ZoneDivision</i></a>: Run a parcel division algorithm on an input zone</li>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/workflow/ConsolidationDivision.java"><i>ConsolidationDivision</i></a>: Merges predesigned parcels together and run a decomposition task</li>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/workflow/Densification.java"><i>Densification</i></a>: Run the densification process on the predesigned parcels</li>
    </ul>
</li>
<li><b>parcelFunction</b>: contains functions for single parcels, in particular sorting functions and marking functions.</li>
<li><b>scenario</b>: contains the objects used for processing an automated simulation on a large zone (<a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">technical description can be found here</a>).</li>
<li><b>usecase</b>: contains predefined use cases that can be directly run.</li>
</ul>
<br />
