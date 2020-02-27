# Parcel Manager

Funtion to operate various parcel reshaping operations.
Documentation can be found on a coming article and on the PhD thesis of Maxime Colomb.
The easiest way of using this library without coming to the code is to create scenarios using .json files ([see here](https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md)).
Uses a lot of function from the library [ArtiScales-tools](https://github.com/ArtiScales/ArtiScales-tools) (no stable version yet)

Still under developpement.

## Description of packages 

<ul>
<li><b>analysis</b>: Contains different automated analysis processes.</li>
<li><b>decomposition</b>: Contains the different decomposition algorithms</li>
<li><b>fields</b>: Contains methods and tools to automatically set feature informations on the generated parcels. Solutions for french parcels types and ArtiScales parcels types are implemented </li>
<li><b>goal</b>: Contains the different goals for the parcel manager process. Three solutions are now implemented : 
    <ul>
        <li><i>ParcelTotRecomp</i>: Run a parcel division algorithm on an input zone</li>
        <li><i>ParcelConsolidRecomp</i>: Merges predesigned parcel together and run a decomposition task</li>
        <li><i>ParcelDensification</i>: Run the densification process on the predesigned parcels</li>
    </ul>
</li>
<li><b>scenario</b>: Contains objects used for processing an automated simulation on a large zone ([technical description can be found here](https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md))</li>
</ul>
