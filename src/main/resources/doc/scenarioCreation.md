<h1>Ceration of Parcel Manager scenarios with .JSON file</h1>


To create scenarios, it is possible to use a list of parameter stocked in a .json file.
This file is then an argument for the creation of a <i>PMScenario</i> object.
Parameters of the .json file must respect the specifications listed below (***bold + italic*** words represent a .json parameter):

<h2>File location</h2>
There is two ways to set the location of the input shapefiles.
It is possible to set every files with the following names:

  * <i><b>zoningFile</b></i>: shapefile representing the zoning plan 
  * <i><b>buildingFile </b></i>: shapefile representing the building on the 
  * <i><b>communityFile</b></i>: shapefile representing the community limits. It must have a field containing the city's code (<i>INSEE</i> by default)
  * <i><b>predicateFile</b></i>: .csv file containing the rules of the (see xxx in the ArtiScales project to correctly set those rules)
  * <i><b>parcelFile</b></i>: shapefile representing the parcels. By default, it should respect french attributes (new schemas can be implemented). 
  * <i><b>polygonIntersection </b></i>: shapefile containing polygons which represent an interest for the parcel to be urbanized (it can be null).
  * <i><b>profileFolder</b></i>: folder where the building profile must be stored under .json names (see xxx for doc about those folders)
  * <i><b>outFolder</b></i>: folder where the result are stored

It is also possible to set a root folder where every files are store. 
They have the same names that the parameters previously cited (+ the .shp attribute). 
Complete folder path is set at the parameter <i><b>rootfile</b></i>. 
File names set this way would be overwritten if a file path is directly set with the parameter previously cited.

<h2>Parcel selection</h2>
It is possible to set two different types of parcel selection regarding their inclusion in a part of the zoning plan or a community. 

* <i><b>communityNumber</i></b>: Select every parcels that composed the community corresponding to the code. The default field name is <b>INSEE</b>.
* <i><b>communityType</i></b>: A type of urban tissue. All the communities that follows this type will be selected. Default field name is <i>armature</i>. Doesn't work if a <i><b>communityNumber</i></b> is set. 

If those two parameters are not set, every parcels are selected and taken into acount. 

<h2>Parcel marks</h2>

It is often needed to mark a set of parcels in order to declare that their reshaping must happen.
The field *SPLIT* is used as default.
It is possible to use different method to mark the parcels. 
The parameter ***zone*** is used to select a type of zoning (declared with the ***zoningFile*** shapefile).
It is also possible to mark the parcel using the superposition with a set of polygons. 
Every parcels that intersects the set of polygons from the ***polygonIntersection*** shapefile are marked.

<h2>Parcel Manager algorithms</h2>
Different algorithm are available in Parcel Manager.
The parameter ***algo*** can be set with one of those three values:

* ***totalZone***: They take a total zone as an input and decompose it as a big zone
* ***consolid***: Takes a set of marked parcels as an input and decompose them as contiguious zones
* ***dens***: Takes a set of marked parcels as an input and try to densify them with the **parcel flag** process

<h2>Parcel Manager process</h2>
Diferrent process can be used to divise parcels.
The parameter ***parcelProcess*** can be set with one of those three values: 

* ***OBB***: use the oriented bounding box method
* ***SS***: use the straight skeleton method
* ***MS***: use the median line skeleton method

<h2>Building profiles</h2>

It is possible to set different type of parcel tissues that would lead to the construction of buildings. 
The java object parameter.ProfileBuilding from the ArtiScales-tools project is used. 

