# Field Attributes
## Parcel
Parcel Manager is made to be adaptable for every parcels nomenclature.
It simply works with three basic fields, defined as follow:
<ul>
    <li>The <i>community field</i> represent the unique identifier of the community (it can be a zipcode or a statistical code). Its default name is '<b>DEPCOM</b>'</li>
    <li>The <i>section field</i> represent a zone which must be unique for each communities. Its default name is '<b>SECTION</b>'</li>
    <li>The <i>number field</i> represent a unique number of parcel within each section. Its default name is '<b>NUMERO</b>'</li>
</ul>
Those three attributes are generated and dealt with in every Parcel Manager workflows.
Reshaped parcels have a new <i>section</i> value, based on the value of the <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/pm/workflow/Workflow.java">abstract workflow.Workflow.makeNewSection()</a> method and incremented.
<i>Number</i> value is also incremented for each new zone.

If the input parcels already contains attributes that share the same field name, they are copied for every parcels. 
To match the field names of the input parcels, change them with the static setters from the <a href="https://github.com/ArtiScales/ArtiScales-tools/blob/master/src/main/java/fr/ign/artiscales/pm/parcelFunction/ParcelSchema.java">parcelFunction.ParcelSchema</a> class.
<!--If the correspondance is missing with the input data, the unsimuled parcels will have null fields.--> 
It is also possible to convert the parcel type to this minimum type, with methods like <i>frenchParcelToMinParcel(...)</i> from the <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/pm/fields/french/FrenchParcelFields.java">fields.french.FrenchParcelFields</a> class.
Other methods such as <i>setOriginalFrenchParcelAttributes(...)</i> helps re-assignate the fields after a Parcel Manager simulation.
It is possible to copy those methods to adapt them for another parcel nomenclature.

##Zoning plan

The zoning plan also have specific attribute nomenclature. It must contains two specific fields:

<ul>
    <li>a <i>generic name</i> represent the general permission of the zones. Its default name is '<b>TYPEZONE</b>' and can be of three different types:
<ul>
    <li> <b>U</b>: Already urbanized land,</li>
    <li> <b>AU</b>: Not urbanized land but open to new developments,</li>
    <li> <b>N</b>: Not urbanized land and not open to new developments.</li>
</ul>
This field is the only one used during the Parcel Manager simulations</li>
    <li>A <i>precise name</i> which precise special rules on a zone. Its default name is '<b>LIBELLE</b>'.</li>
</ul>

Parcel Manager has always been used in a French context, so we have only implemented the French attribute style. We also implemented attributes for ArtiScales simulations, which extends the French Parcels attribute and add specific fields. 
It is nevertheless possible and easy for a programmer to implement new parcel styles and replace the french methods with it in the workflows.
The <i>fields.GeneralField.parcelFieldType</i> value must be changed and methods using this value must be fulfilled. 
