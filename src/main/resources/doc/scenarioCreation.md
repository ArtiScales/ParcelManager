<h1>Creation of Parcel Manager scenarios with .JSON file</h1>

Scenarios are described in a .json file which stores an ordered series of steps containing a list of parameters.
This file is then an argument for the creation of a <i>PMScenario</i> object.
Parameters of the .json file must respect the specifications listed below (***bold + italic*** words represent a .json parameter):

<h2>File location</h2>
There is two ways to set the location of the input Geopackages.
It is possible to set every files with the following names:  

  * ***parcelFile***: Geopackage representing the parcels. By default, it should respect the existing attribute nomeclature (new schemas can be implemented).
  * ***zoningFile***: Geopackage representing the zoning plan.
  * ***buildingFile***: Geopackage representing the buildings.
  * ***communityFile***: Geopackage representing the boundaries of communities. It must have a field containing the city's code (<i>DEPCOM</i> by default) (can be ***NULL***).
  * ***predicateFile***: .csv file containing specific urban rules. (Look at the ArtiScales project to correctly set those rules) (can be ***NULL***).
  * ***polygonIntersection***: Geopackage containing polygons which represent an interest for the parcel to be urbanized (can be ***NULL***).
  * ***profileUrbanFabric***: folder where the urban fabric profiles are stored under .json names <!-- (see xxx for doc about those folders)-->
  * ***outFolder***: folder where the simulation results are stored

It is also possible to set a root folder where every files are stored. 
Files have the same names as the parameters previously enumerated (+ the .gpkg attribute). 
Complete folder path is set by the parameter ***rootfile***. 
File names set this way would be overwritten if a file path is directly set with the parameter previously cited.

<h2>Parcel Manager Steps</h2>

A Parcel Manager step is a specific process of parcel reshaping applied on a specific zone.
It is possible to create an unlimited list of steps that will apply as a queue.
The parameter ***step*** must contain a .json table with the following arguments.

* ***communityNumber***
* ***communityType***
* ***genericZone***
* ***preciseZone***
* ***workflow***
* ***parcelProcess***
* ***urbanFabric***
* ***optionnal***

<h3>Parcel selection</h3>
It is possible to set two different types of parcel selection regarding their inclusion in a part of the zoning plan or a community. 

* ***communityNumber***: Select every parcels that composed the community corresponding to the code. The default field name is <b>
</b>.
* ***communityType***: A type of urban fabric. All the communities that correspond this type will be selected. Default field name is <i>armature</i>. It doesn't work if a <i><b>communityNumber</b></i> is set. 

If those two parameters are not set, every parcels are selected and taken into account in the simulation. 

<h3>Parcel marks</h3>
It is often needed to mark a set of parcels in order to declare that their reshaping must happen.
The field <b>SPLIT</b> is used by default but can be changed with the <i>parcelFunction.MarkParcelAttributeFromPosition.setMarkFieldName(String)</i> function.
The first way to mark parcels is to use a set of polygons. 
All parcels that intersect the set of polygons from the <i><b>polygonIntersection</b></i> Geopackage are marked.

<h3>Integration of zoning plans</h3>
It is also possible to use attribute information with a Geopackage, such as a zoning plan, to mark the parcels.
The <b><i>zoningFile</i></b> plan supports a special integration.
The parameter <b><i>genericZone</i></b> is used to select a type of zoning and can recover multiple zone names using dedicated functions, such as <i>fields.FrenchZoningSchemas.normalizeNameFrenchBigZone()</i>. 
More specific zones are set using the parameter <b><i>preciseZone</i></b>. 
The setting of a <b><i>preciseZone</i></b> parameter requires to previously set a <b><i>genericZone</i></b>. 
If a <b><i>genericZone</i></b> is set without <b><i>preciseZone</i></b>, all zones will be simulated. 
If we define a step for a <b><i>preciseZone</i></b> and a second step for the rest of its <b><i>genericZone</i></b>, the results of the <b><i>preciseZone</i></b> step will not be part of the second step simulation.
Though, make sure that the <b><i>preciseZone</i></b> step is declared before the <b><i>genericZone</i></b>.


It is possible to hack this method by using another kind of Geopackage and call it in the <b><i>zoningFile</i></b>, to change the default value of the <b><i>genericZone</i></b> field name with the <i>fields.GeneralFields.setZoneGenericNameField(String)</i> method, and to set a <b><i>genericZone</i></b> value. 

Once a parcel has been simulated, its attribute filed <i>SECTION</i> (see attribute policy) is marked with a long value, depending on the <b><i>workflow</i></b> used. By default, parcels cannot be marked again if they have been already simulated. It is possible to change this through the setting of the static boolean <b>MarkParcelAttributeFromPosition.postMark</b> to <b>true</b>.

<h3>Parcel Manager Workflows</h3>

By default, the parameter ***workflow*** can be set with one of those three values:

* ***zoneDivision***: They take a total zone as an input and decompose it as a big zone
* ***consolidationDivision***: Takes a set of marked parcels as an input and decompose them as contiguious zones
* ***densification***: Takes a set of marked parcels as an input and try to densify them with the **parcel flag** process

<h3>Parcel Manager processes</h3>
Different processes can be used to divise parcels.
The parameter <b><i>parcelProcess</i></b> can be set with one of those three values: 

* ***OBB***: use the oriented bounding box method
* ***SS***: use the straight skeleton method
* ***MS***: use the median line skeleton method (not implemented yet)

<h3>Urban Fabric profiles</h3>

It is possible to set different types of urban fabric that define the types of buildings targeted to be constructed. 
The java object **parameter.ProfileUrbanFabric** from the ArtiScales-tools project is used. 
The ***urbanFabric*** parameter defines the profile of the parcel parameters.
Examples can be found in the folders of *use cases*


<h3>Optional</h3>
Few special parameters can be added to a scenario. 
They concern either processes or workflows.
In order to be taken into account, the name of the parameter should be <i>optional</i> and its value has to be separated by a ':' 
Parameters can be:

* ***peripheralRoad*** (only for **Straight Skeleton** process). Generates of a peripheral road around the initial zone (<i>"optional":"peripheralRoad:true"</i>).
* ***adaptAreaOfUrbanFabric*** On-the-fly change of the parameters maximal area and minimal area of urban fabric profiles (<i>"optional":"adaptAreaOfUrbanFabric"</i>. As this option is disabled by default, there's no need to add a : value).
* ***keepExistingRoad*** (only for **Zone Division** workflow). If true, spaces that correspond to a road or a public space will not be reshaped. If false, the whole zone will be reshaped.

