<h1>Creation of Parcel Manager scenarios with .JSON file</h1>

Scenarios can be described with a .json file which stores a suite of steps, which contains a list of parameter .
This file is then an argument for the creation of a <i>PMScenario</i> object.
Parameters of the .json file must respect the specifications listed below (***bold + italic*** words represent a .json parameter):

<h2>File location</h2>
There is two ways to set the location of the input Geopackages.
It is possible to set every files with the following names:  

  * ***parcelFile***: Geopackage representing the parcels. By default, it should respect french attributes (new schemas can be implemented).
  * ***zoningFile***: Geopackage representing the zoning plan.
  * ***buildingFile***: Geopackage representing the buildings.
  * ***communityFile***: Geopackage representing the community limits. It must have a field containing the city's code (<i>DEPCOM</i> by default) (can be ***NULL***).
  * ***predicateFile***: .csv file containing specific urban rules. Based on the ArtiScales project to correctly set those rules) (can be ***NULL***).
  * ***polygonIntersection***: Geopackage containing polygons which represent an interest for the parcel to be urbanized (can be ***NULL***).
  * ***profileUrbanFabric***: folder where the urban fabric profile are stored under .json names <!-- (see xxx for doc about those folders)-->
  * ***outFolder***: folder where the result are stored

It is also possible to set a root folder where every files are store. 
They have the same names that the parameters previously cited (+ the .gpkg attribute). 
Complete folder path is set at the parameter ***rootfile***. 
File names set this way would be overwritten if a file path is directly set with the parameter previously cited.

<h2>Parcel Manager Steps</h2>

A Parcel Manager step is a specific process of parcel reshaping applied on a specific zone.
It is possible to create an unlimited list of step that will apply as a queue.
The parameter ***step*** must contain a .json table with the following arguments.

* ***communityNumber***
* ***communityType***
* ***genericZone***
* ***preciseZone***
* ***workflow***
* ***parcelProcess***
* ***urbanFabric***

<h3>Parcel selection</h3>
It is possible to set two different types of parcel selection regarding their inclusion in a part of the zoning plan or a community. 

* ***communityNumber***: Select every parcels that composed the community corresponding to the code. The default field name is <b>
</b>.
* ***communityType***: A type of urban tissue. All the communities that follows this type will be selected. Default field name is <i>armature</i>. That doesn't work if a <i><b>communityNumber</b></i> is set. 

If those two parameters are not set, every parcels are selected and taken into acount. 

<h3>Parcel marks</h3>
It is often needed to mark a set of parcels in order to declare that their reshaping must happen.
The field <b>SPLIT</b> is used as default but can be changed with the <i>parcelFunction.MarkParcelAttributeFromPosition.setMarkFieldName(String)</i> function.
The first way to mark the parcel is to use a set of polygons. 
Every parcels that intersects the set of polygons from the <i><b>polygonIntersection</b></i> Geopackage are marked.

<h3>Integration of zoning plans</h3>
It is also possible to use attribute information with a Geopackage, such as a zoning plan, to mark the parcels.
The <b><i>zoningFile</i></b> plan supports a special integration.
The parameter <b><i>genericZone</i></b> is used to select a type of zoning and can recover multiple zone names using dedicated functions, such as <i>fields.FrenchZoningSchemas.normalizeNameFrenchBigZone()</i>. 
More particular zones are set using the parameter  <b><i>preciseZone</i></b>. 
The setting of a <b><i>preciseZone</i></b> parameter doesn't exempt to set a <b><i>genericZone</i></b>. 
Thus, if a <b><i>genericZone</i></b> is set without <b><i>preciseZone</i></b>, every zones will be simulated. 
If we define a step for a <b><i>preciseZone</i></b> and a second step for the rest of its <b><i>genericZone</i></b>, the results of the <b><i>preciseZone</i></b> step will not be part of the second step simulation.
Though, make sure that the <b><i>preciseZone</i></b> step is declared before the <b><i>preciseZone</i></b>.


It is possible to hack this method by using another kind of Geopackage and put in on the <b><i>zoningFile</i></b>, change the default value of the <b><i>genericZone</i></b> field name with the <i>fields.GeneralFields.setZoneGenericNameField(String)</i> method and set a <b><i>genericZone</i></b> value. 

Once a parcel has been simulated, it attribute filed <i>SECTION</i> (see attribute policy) is marked with a long value, depending on the <b><i>workflow</i></b> used. We check that field to know if a parcel has been simulated. By default, parcels cannot be marked again if they have been already simulated. It is possible to change that behaviour, for post treatments means by exemple, in setting the static boolean <b>MarkParcelAttributeFromPosition.postMark</b> to <b>true</b>.

<h3>Parcel Manager Workflows</h3>
Different algorithm are available in Parcel Manager.
The parameter <b><i>workflow</i></b> can be set with one of those three values:

* ***zoneDivision***: They take a total zone as an input and decompose it as a big zone
* ***consolidationDivision***: Takes a set of marked parcels as an input and decompose them as contiguious zones
* ***densification***: Takes a set of marked parcels as an input and try to densify them with the **parcel flag** process

<h3>Parcel Manager process</h3>
Diferrent process can be used to divise parcels.
The parameter <b><i>parcelProcess</i></b> can be set with one of those three values: 

* ***OBB***: use the oriented bounding box method
* ***SS***: use the straight skeleton method
* ***MS***: use the median line skeleton method (not implemented yet)

<h3>Urban Fabric profiles</h3>

It is possible to set different type of urban fabric that would lead to the construction of buildings. 
The java object **parameter.ProfileUrbanFabric** from the ArtiScales-tools project is used. 
The ***urbanFabric*** parameter defines the profile of the parcels parameters.
Examples can be found in the folders of every *use cases*

