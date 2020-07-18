# Field Attributes
Parcel Manager is made to be adaptable for every input of parcels. 
It works with three basic fields, defined as follow:
<ul>
    <li>The <i>community field</i> represent the unique identifier of the community (it can be a zipcode or a statistical code). Its default name is '<b>DEPCOM</b>'</li>
    <li>The <i>section field</i> represent a zone which must be unique for each communities. Its default name is '<b>SECTION</b>'</li>
    <li>The <i>number field</i> represent a unique number of parcel within each section. Its default name is '<b>NUMERO</b>'</li>
</ul>
Those attribute are generated during every Parcel Manager process.
If the input parcels already contains those attributes, they are copied to the not simuled parcels. If the field names are different, it is possible to change them with the static setters from the <a href="https://github.com/ArtiScales/ArtiScales-tools/blob/master/src/main/java/fr/ign/cogit/parcelFunction/ParcelSchema.java">parcelFunction.ParcelSchema class</a>.
If the correspondance is missing with the input data, the unsimuled parcels will have null fields. 
It is also possible to convert the parcel type to this minimum type, with methods like <i>frenchParcelToMinParcel(...)</i> from the <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/fields/french/FrenchParcelFields.java">fields.french.FrenchParcelFields class</a>.
Other methods such as <i>setOriginalFrenchParcelAttributes(...)</i> helps re-assignate the fields after a Parcel Manager simulation.

The zoning plan also have specific attribute policy. It must contains two specific fields:

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
