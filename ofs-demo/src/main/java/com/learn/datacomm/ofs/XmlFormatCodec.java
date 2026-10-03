package com.learn.datacomm.ofs;

/**
 * ==== FORMAT XML OFS ====
 *
 * Selain native format, FLEXCUBE/OFS juga mendukung representasi XML untuk
 * pesan yang sama. Bedanya dengan native format: field-nya SELF-DESCRIBING
 * (nama field eksplisit ada di tag), jadi lebih gampang dibaca manusia dan
 * lebih toleran terhadap urutan field -- mirip semangat JSON di REST, tapi
 * dalam sintaks XML yang lebih verbose.
 *
 * Trade-off yang kelihatan kalau dibandingkan native format:
 *   - Ukuran pesan XML jauh lebih besar untuk data yang sama (tag pembuka/
 *     penutup, whitespace) -- coba bandingkan panjang stringnya saat demo.
 *   - Parsing XML butuh library/parser XML (di sini pakai javax.xml.parsers
 *     bawaan JDK), sementara native format cukup String.split().
 *   - XML lebih toleran terhadap field baru ditambahkan tanpa merusak
 *     parser lama (asal parser membaca by tag name, bukan by posisi).
 *
 * Sengaja dibangun/diparsing manual pakai StringBuilder + DOM parser bawaan
 * JDK (bukan library XML binding seperti JAXB) supaya terlihat jelas
 * mekanismenya, bukan disembunyikan di balik anotasi.
 */
public class XmlFormatCodec {

    public static String encodeRequest(TransferMessage msg) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<OFS_MESSAGE>\n");
        xml.append("  <HEADER><FUNCTION_ID>FTXFRQ</FUNCTION_ID><TYPE>IN</TYPE></HEADER>\n");
        xml.append("  <BODY>\n");
        xml.append("    <SOURCE_ACCOUNT>").append(msg.getSourceAccount()).append("</SOURCE_ACCOUNT>\n");
        xml.append("    <DESTINATION_ACCOUNT>").append(msg.getDestinationAccount()).append("</DESTINATION_ACCOUNT>\n");
        xml.append("    <AMOUNT>").append(msg.getAmount()).append("</AMOUNT>\n");
        xml.append("    <REFERENCE_ID>").append(msg.getReferenceId()).append("</REFERENCE_ID>\n");
        xml.append("  </BODY>\n");
        xml.append("</OFS_MESSAGE>");
        return xml.toString();
    }

    public static TransferMessage decodeRequest(String xml) throws Exception {
        org.w3c.dom.Document doc = parse(xml);
        String sourceAccount = textOf(doc, "SOURCE_ACCOUNT");
        String destinationAccount = textOf(doc, "DESTINATION_ACCOUNT");
        long amount = Long.parseLong(textOf(doc, "AMOUNT"));
        String referenceId = textOf(doc, "REFERENCE_ID");
        return new TransferMessage(sourceAccount, destinationAccount, amount, referenceId);
    }

    public static String encodeResponse(String referenceId, String status, String message) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<OFS_MESSAGE>\n");
        xml.append("  <HEADER><FUNCTION_ID>FTXFRQ</FUNCTION_ID><TYPE>OUT</TYPE></HEADER>\n");
        xml.append("  <BODY>\n");
        xml.append("    <REFERENCE_ID>").append(referenceId).append("</REFERENCE_ID>\n");
        xml.append("    <STATUS>").append(status).append("</STATUS>\n");
        xml.append("    <MESSAGE>").append(message).append("</MESSAGE>\n");
        xml.append("  </BODY>\n");
        xml.append("</OFS_MESSAGE>");
        return xml.toString();
    }

    /** Mengembalikan array [referenceId, status, message]. */
    public static String[] decodeResponse(String xml) throws Exception {
        org.w3c.dom.Document doc = parse(xml);
        return new String[]{textOf(doc, "REFERENCE_ID"), textOf(doc, "STATUS"), textOf(doc, "MESSAGE")};
    }

    private static org.w3c.dom.Document parse(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    private static String textOf(org.w3c.dom.Document doc, String tagName) {
        return doc.getElementsByTagName(tagName).item(0).getTextContent();
    }
}
