# Field Attributes

## Parcel Marking
A specific attribute is required to define which parcels can be reshaped by simulation. By default, this specific atribute is set in a field named <b>SPLIT</b>. If the attribute value is set to 1, the corresponding parcel can be reshaped. Methods from the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/parcelFunction/MarkParcelAttributeFromPosition.java">MarkParcelAttributeFromPosition</a> class can be used to automatically set the attribute values of the field <b>SPLIT</b> for all parcels. 

## Parcel ID

Parcel Manager is made to be adaptable for every parcels nomenclature.
It simply works with three basic fields, defined as follow:
<ul>
    <li>The <i>community field</i> represent the unique identifier of the community (it can be a zipcode or a statistical code). Its default name is '<b>DEPCOM</b>'</li>
    <li>The <i>section field</i> represent a zone which must be unique for each communities. Its default name is '<b>SECTION</b>'</li>
    <li>The <i>number field</i> represent a unique number of parcel within each section. Its default name is '<b>NUMERO</b>'</li>
</ul>
Those three attributes are generated and dealt with in every Parcel Manager workflows.
Reshaped parcels have a new <i>section</i> value, based on the value of the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/workflow/Workflow.java">abstract workflow.Workflow.makeNewSection()</a> method. This value is automatically incremented.
<i>Number</i> value is also incremented for each new zone.

If the input parcels already contains attributes that share the same field name, they are copied for every parcels. 
To match the field names of the input parcels, change them with the static setters from the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/parcelFunction/ParcelSchema.java">parcelFunction.ParcelSchema</a> class.
<!--If the correspondance is missing with the input data, the unsimuled parcels will have null fields.--> 
It is also possible to convert the parcel type to this minimum type, with methods like <i>frenchParcelToMinParcel(...)</i> from the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/fields/french/FrenchParcelFields.java">fields.french.FrenchParcelFields</a> class.
Other methods such as <i>setOriginalFrenchParcelAttributes(...)</i> helps re-assignate the fields after a Parcel Manager simulation.
It is possible to copy those methods to adapt them for another parcel nomenclature.

## Zoning plan

The attribute table of a zoning plan must contain two fields:

<ul>
    <li>a <i>generic name</i> sets the general authorization of each zone of the zoning plan. Its default name is '<b>TYPEZONE</b>'. Its default attribute values are:
<ul>
    <li> <b>U</b>: Fully urbanized zone,</li>
    <li> <b>AU</b>: Non urbanized zone in which new developments are authorized,</li>
    <li> <b>N</b>: Non urbanized zone in which new developments are not authorized.</li>
</ul>
    <li>A <i>precise name</i> which precise special rules on a zone. Its default name is '<b>LIBELLE</b>'.</li>
</ul>

The default attribute tables of Parcel Manager can be modified easily.
To do this, the <i>fields.GeneralField.parcelFieldType</i> value must be changed and methods using this value must be fulfilled. 
