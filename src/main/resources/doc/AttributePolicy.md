# Field Attributes

## Parcel Marking
A specific attribute is required to define which parcels can be reshaped by simulation. By default, this specific atribute is set in a field named <b>SPLIT</b>. If the attribute value is set to 1, the corresponding parcel can be reshaped. Methods from the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/parcelFunction/MarkParcelAttributeFromPosition.java">MarkParcelAttributeFromPosition</a> class can be used to automatically set the attribute values of the field <b>SPLIT</b> for all parcels. 

## Parcel ID

The parcel nomenclature of Parcel Manager simply works with three basic fields, defined as follow:
<ul>
    <li>The <i>community field</i> sets the unique identifier of the community (it can be a zipcode or a statistical code). Its default name is '<b>DEPCOM</b>'</li>
    <li>The <i>section field</i> identifies a zone, which must be unique in each community. Its default name is '<b>SECTION</b>'</li>
    <li>The <i>number field</i> is a unique number that identifies each parcel within each section. Its default name is '<b>NUMERO</b>'</li>
</ul>
Those three attributes are used in all Parcel Manager workflows.
Reshaped parcels have a new <i>section</i> value, based on the value of the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/workflow/Workflow.java">abstract workflow.Workflow.makeNewSection()</a> method. This value is automatically incremented.
<i>Number</i> value is also incremented for each new zone.

It is possible to convert the basic nomemclature of Parcel Manager into another nomenclature and vice versa using specific methods. See for instance <i>frenchParcelToMinParcel(...)</i> from the <a href="https://framagit.org/artiscales/parcelmanager/-/blob/master/src/main/java/fr/ign/artiscales/pm/fields/french/FrenchParcelFields.java">fields.french.FrenchParcelFields</a> class, and  <i>setOriginalFrenchParcelAttributes(...)</i>.

## Zoning plan

The attribute table of a zoning plan must contain two fields:

<ul>
    <li>a <i>generic name</i> sets the general characteristics of each zone of the zoning plan. Its default name is '<b>TYPEZONE</b>'. Its default attribute values are:
<ul>
    <li> <b>U</b>: Fully urbanized zone,</li>
    <li> <b>AU</b>: Non urbanized zone in which new developments are authorized,</li>
    <li> <b>N</b>: Non urbanized zone in which new developments are not authorized.</li>
</ul>
    <li>a <i>precise name</i> which defines special rules that apply to a zone. Its default name is '<b>LIBELLE</b>'.</li>
</ul>

The default attribute tables of Parcel Manager can be modified easily.
To do this, the <i>fields.GeneralField.parcelFieldType</i> value must be changed and methods using this value must be fulfilled. 
