<h1>Creation of Parcel Manager scenarios with .JSON file</h1>

Scenarios can be described with a .json file which stores a suite of steps, which contains a list of parameter .
This file is then an argument for the creation of a <i>PMScenario</i> object.
Parameters of the .json file must respect the specifications listed below (***bold + italic*** words represent a .json parameter):

<h2>File location</h2>
There is two ways to set the location of the input shapefiles.
It is possible to set every files with the following names:  

  * ***parcelFile***: shapefile representing the parcels. By default, it should respect french attributes (new schemas can be implemented).
  * ***zoningFile***: shapefile representing the zoning plan.
  * ***buildingFile***: shapefile representing the buildings.
  * ***communityFile***: shapefile representing the community limits. It must have a field containing the city's code (<i>INSEE</i> by default) (can be ***NULL***).
  * ***predicateFile***: .csv file containing the rules of the (see xxx in the ArtiScales project to correctly set those rules) (can be ***NULL***).
  * ***polygonIntersection***: shapefile containing polygons which represent an interest for the parcel to be urbanized (can be ***NULL***).
  * ***profileUrbanFabric***: folder where the urban fabric profile are stored under .json names <!-- (see xxx for doc about those folders)-->
  * ***outFolder***: folder where the result are stored

It is also possible to set a root folder where every files are store. 
They have the same names that the parameters previously cited (+ the .shp attribute). 
Complete folder path is set at the parameter ***rootfile***. 
File names set this way would be overwritten if a file path is directly set with the parameter previously cited.

<h2>Parcel Manager Steps</h2>

A Parcel Manager step is a specific process of parcel reshaping applied on a specific zone.
It is possible to create an unlimited list of step that will apply as a queue.
The parameter ***step*** must contain a .json table with all the following arguments.

* ***communityNumber***
* ***communityType***
* ***genericZone***
* ***preciseZone***
* ***goal***
* ***parcelProcess***
* ***urbanFabric***

<h3>Parcel selection</h3>
It is possible to set two different types of parcel selection regarding their inclusion in a part of the zoning plan or a community. 

* ***communityNumber***: Select every parcels that composed the community corresponding to the code. The default field name is <b>INSEE</b>.
* ***communityType***: A type of urban tissue. All the communities that follows this type will be selected. Default field name is <i>armature</i>. That doesn't work if a ***communityNumber*** is set. 

If those two parameters are not set, every parcels are selected and taken into acount. 

<h3>Parcel marks</h3>
It is often needed to mark a set of parcels in order to declare that their reshaping must happen.
The field *SPLIT* is used as default but can be changed with the *parcelFunction.MarkParcelAttributeFromPosition.setMarkFieldName(String)* function.
The first way to mark the parcel is to use a set of polygons. 
Every parcels that intersects the set of polygons from the ***polygonIntersection*** shapefile are marked.

<h3>Integration of zoning plans</h3>
It is also possible to use attribute information with a shapefile, such as a zoning plan, to mark the parcels.
The ***zoningFile*** shapefile supports a special integration.
The parameter ***genericZone*** is used to select a type of zoning and can recover multiple zone names using dedicated functions, such as *fields.FrenchZoningSchemas.normalizeNameFrenchBigZone()*. 
More particular zones are set using the parameter  ***preciseZone***. 
The setting of a ***preciseZone*** parameter doesn't exempt to set a ***genericZone***. 
Thus, if a ***genericZone*** is set without ***preciseZone***, every zones will be simulated. 
If we define a step for a ***preciseZone*** and a second step for the rest of its ***genericZone***, the results of the ***preciseZone*** step will not be part of the second step simulation.
Though, make sure that the ***preciseZone*** step is declared before the ***preciseZone***.


It is possible to hack this method by using another kind of shapefile and put in on the ***zoningFile***, change the default value of the ***genericZone*** field name with the *fields.GeneralFields.setZoneGenericNameField(String)* method and set a ***genericZone*** value. 

Once a parcel has been simulated, it attribute filed *SECTION* (see attribute policy) is marked with a long value, depending on the ***goal*** used. We check that field to know if a parcel has been simulated. By default, parcels cannot be marked again if they have been already simulated. It is possible to change that behaviour, for post treatments means by exemple, in setting the static boolean ***MarkParcelAttributeFromPosition.postMark*** to **true**.

<h3>Parcel Manager algorithms</h3>
Different algorithm are available in Parcel Manager.
The parameter ***goal*** can be set with one of those three values:

* ***zoneDivision***: They take a total zone as an input and decompose it as a big zone
* ***consolidationDivision***: Takes a set of marked parcels as an input and decompose them as contiguious zones
* ***densification***: Takes a set of marked parcels as an input and try to densify them with the **parcel flag** process

<h3>Parcel Manager process</h3>
Diferrent process can be used to divise parcels.
The parameter ***parcelProcess*** can be set with one of those three values: 

* ***OBB***: use the oriented bounding box method
* ***SS***: use the straight skeleton method (not implemented yet)
* ***MS***: use the median line skeleton method (not implemented yet)

<h3>Urban Fabric profiles</h3>

It is possible to set different type of urban fabric that would lead to the construction of buildings. 
The java object **parameter.ProfileUrbanFabric** from the ArtiScales-tools project is used. 
The ***urbanFabric*** parameter defines the profile of the parcels parameters.

