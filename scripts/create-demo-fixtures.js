// Fictional text PDFs for checking the OCR/review/export pipeline on a device.
// These are illustrative test documents, not SEC forms or real registrations.
const fs = require('node:fs');
const path = require('node:path');
const folder = path.join(__dirname, '..', 'demo-documents');
fs.mkdirSync(folder, { recursive: true });
function pdf(filename, lines) {
  const escaped = s => s.replace(/([\\()])/g, '\\$1');
  const content = 'BT /F1 18 Tf 56 750 Td 28 TL\n' + lines.map((line, i) => `${i ? 'T* ' : ''}(${escaped(line)}) Tj`).join('\n') + '\nET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(content, 'ascii')} >>\nstream\n${content}\nendstream`
  ];
  let document = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, i) => { offsets.push(Buffer.byteLength(document, 'ascii')); document += `${i + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(document, 'ascii');
  document += `xref\n0 ${offsets.length}\n0000000000 65535 f \n` + offsets.slice(1).map(n => `${String(n).padStart(10, '0')} 00000 n \n`).join('');
  document += `trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  fs.writeFileSync(path.join(folder, filename), document, 'ascii');
}
const top = ['FICTIONAL DEMO - NOT AN OFFICIAL DOCUMENT', '', 'CERTIFICATE EXTRACTION TEST', '', 'This is to certify that'];
pdf('scan001.pdf', [...top, 'NORTHSTAR TRADING CORPORATION', 'has been registered.', '', 'Illustrative data only. No real registration.']);
pdf('scan002.pdf', [...top, 'NORTHSTAR TRADING CORPORATION', 'has been registered.', '', 'Second file to test duplicate numbering.']);
pdf('scan003.pdf', [...top, 'RIVERBEND SERVICES INC.', 'has been registered.', '', 'Illustrative data only.']);
pdf('needs-review.pdf', ['FICTIONAL DEMO - NOT AN OFFICIAL DOCUMENT', '', 'Company mentioned: ALPHA INC.', 'Another company mentioned: BETA INC.', '', 'No certificate-holder anchor. Review required.']);
console.log('Created four fictional PDF fixtures in demo-documents.');
