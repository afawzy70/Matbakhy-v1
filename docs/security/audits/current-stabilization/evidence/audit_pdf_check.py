"""Compile the unchanged PDF inspector with an isolated temporary JDK and benign fixture."""
import hashlib, json, pathlib, shutil, subprocess, tarfile, urllib.request
ROOT=pathlib.Path('/home/aiagent/workspaces/agents/security')
OUT=ROOT/'docs/security/audits/current-stabilization/evidence'
TMP=pathlib.Path('/tmp/matbakhy-security-pdf'); TMP.mkdir(exist_ok=True)
assets=json.loads(pathlib.Path('/tmp/matbakhy-adoptium-response.txt').read_text())
pkg=assets[0]['binary']['package']; archive=TMP/'jdk.tar.gz'
if not archive.exists(): urllib.request.urlretrieve(pkg['link'],archive)
actual=hashlib.sha256(archive.read_bytes()).hexdigest()
assert actual==pkg['checksum']
if not list(TMP.glob('jdk-*')):
 with tarfile.open(archive) as tf: tf.extractall(TMP,filter='data')
jdk=next(TMP.glob('jdk-*'))
sources=[]
for rel in ['backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaInspector.java','backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaPolicy.java','backend/modules/catalog/src/main/java/com/matbakhy/catalog/api/MediaLibrary.java']:
    target=TMP/pathlib.Path(rel).name; shutil.copyfile(ROOT/rel,target); sources.append(str(target))
harness=TMP/'PdfAudit.java'
harness.write_text('''package com.matbakhy.catalog.internal.media;
import java.nio.charset.StandardCharsets;
public class PdfAudit {
 static String pdf(String name) {
  StringBuilder b=new StringBuilder("%PDF-1.4\\n");
  String[] objects={"<< /Type /Catalog /Pages 2 0 R /OpenAction 4 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>", "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>", "<< /S /"+name+" /J#53 (void(0);) >>"};
  int[] offsets=new int[objects.length];
  for(int i=0;i<objects.length;i++){offsets[i]=b.length(); b.append(i+1).append(" 0 obj\\n").append(objects[i]).append("\\nendobj\\n");}
  int start=b.length(); b.append("xref\\n0 5\\n0000000000 65535 f \\n");
  for(int offset:offsets)b.append(String.format(java.util.Locale.ROOT,"%010d 00000 n \\n",offset));
  return b.append("trailer << /Size 5 /Root 1 0 R >>\\nstartxref\\n").append(start).append("\\n%%EOF\\n").toString();
 }
 public static void main(String[] args) throws Exception {
  for (String name : new String[]{"JavaScript", "J#61vaScript"}) {
   String pdf=pdf(name);
   try { MediaInspector.inspectPdf(pdf.getBytes(StandardCharsets.ISO_8859_1)); System.out.println(name+":ACCEPTED"); }
   catch (MediaInspector.Rejected e) { System.out.println(name+":REJECTED:"+e.reason); }
  }
 }
}
'''.replace('void\\(0\\);','void(0);'))
sources.append(str(harness))
compiled=subprocess.run([str(jdk/'bin/javac'),'-d',str(TMP/'classes'),*sources],capture_output=True,text=True)
result={'jdk':assets[0]['version']['semver'],'jdk_sha256':actual,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'compile_exit':compiled.returncode,'compile_stderr':compiled.stderr}
if compiled.returncode==0:
 p=subprocess.run([str(jdk/'bin/java'),'-cp',str(TMP/'classes'),'com.matbakhy.catalog.internal.media.PdfAudit'],capture_output=True,text=True)
 result.update(run_exit=p.returncode,stdout=p.stdout,stderr=p.stderr)
result['inspector_sha256']=hashlib.sha256((TMP/'MediaInspector.java').read_bytes()).hexdigest()
(OUT/'pdf-inspector-reproduction.json').write_text(json.dumps(result,indent=2)+'\n')
shutil.copyfile(harness,OUT/'PdfAudit.java')
print(json.dumps(result),flush=True)
