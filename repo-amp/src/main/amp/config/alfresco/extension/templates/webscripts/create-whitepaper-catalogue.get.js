var contentType = "myc:whitepaperCatalogue";
var documentName = url.templateArgs.documentName;

var document = companyhome.createNode(documentName, contentType);

if (document != null){
    model.document = document;
    model.msg = "Created whitepaper catalogue OK!";
}
else {
    model.msg = "Failed to create document!";
}
