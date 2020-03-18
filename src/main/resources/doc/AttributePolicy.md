Field Attribute :
The application of Parcel Manager requires some basic fields on the input parcel shapefiles. Their defaut names are the following:
<ul>
    <li>The <i><b>DEPCOM</i></b> fied represent the unique identifier of the community (it can be a zipcode or a statistical code)</li>
    <li>The <i><b>SECTION</i></b> field represent a zone which must be unique for each communities</li>
    <li>The <i><b>NUMERO</i></b> field represent a unique number within each section</li>
</ul>
Those attribute are generated during every Parcel Manager Processes. If input parcels already contains those attributes, they are copied to the not simuled parcels. It is therefore possible to change their names with the setters from the [ParcelSchema class](https://github.com/ArtiScales/ArtiScales-tools/blob/master/src/main/java/fr/ign/cogit/parcelFunction/ParcelSchema.java).
Specific methods contained in the [field package](https://github.com/ArtiScales/ParcelManager/tree/master/src/main/java/fields) helps re-assignate the fields after a Parcel Manager simulation.
Parcel Manager has always been used in a French context. We have only implemented the French attributes. We also implemented attributes for ArtiScales simulations, which retake French Parcels attribute and add specific fields.