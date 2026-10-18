package com.matbakhy.catalog.internal.media;
import java.nio.charset.StandardCharsets;
public class PdfAudit {
 static String pdf(String name) {
  StringBuilder b=new StringBuilder("%PDF-1.4\n");
  String[] objects={"<< /Type /Catalog /Pages 2 0 R /OpenAction 4 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>", "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>", "<< /S /"+name+" /J#53 (void(0);) >>"};
  int[] offsets=new int[objects.length];
  for(int i=0;i<objects.length;i++){offsets[i]=b.length(); b.append(i+1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");}
  int start=b.length(); b.append("xref\n0 5\n0000000000 65535 f \n");
  for(int offset:offsets)b.append(String.format(java.util.Locale.ROOT,"%010d 00000 n \n",offset));
  return b.append("trailer << /Size 5 /Root 1 0 R >>\nstartxref\n").append(start).append("\n%%EOF\n").toString();
 }
 public static void main(String[] args) throws Exception {
  for (String name : new String[]{"JavaScript", "J#61vaScript"}) {
   String pdf=pdf(name);
   try { MediaInspector.inspectPdf(pdf.getBytes(StandardCharsets.ISO_8859_1)); System.out.println(name+":ACCEPTED"); }
   catch (MediaInspector.Rejected e) { System.out.println(name+":REJECTED:"+e.reason); }
  }
 }
}
