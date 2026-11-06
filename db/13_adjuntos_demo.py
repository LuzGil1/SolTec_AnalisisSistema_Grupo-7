# Genera los archivos de las filas de adjunto de 13_datos_demo.sql.
# Son un PNG y un PDF minimos: lo unico que importa es que abran y que el
# tamano y el hash coincidan con los de la base.
#
# Uso, desde la raiz del proyecto:
#   psql -h localhost -U postgres -d soltec_demo -At
#        -c "SELECT ruta FROM soltec.adjunto WHERE ruta LIKE 'demo/%'"
#        | python db/13_adjuntos_demo.py
import hashlib
import os
import struct
import sys
import zlib

BASE = os.path.join("backend", "uploads")


def bloque_png(tipo, datos):
    crc = zlib.crc32(tipo + datos) & 0xFFFFFFFF
    return struct.pack(">I", len(datos)) + tipo + datos + struct.pack(">I", crc)


def crear_png():
    ancho = 64
    alto = 64
    filas = b""
    for y in range(alto):
        filas += b"\x00"
        for x in range(ancho):
            if (x // 8 + y // 8) % 2 == 0:
                filas += b"\xE9\xD5\xFF"
            else:
                filas += b"\x7C\x1F\xC4"
    cabecera = struct.pack(">IIBBBBB", ancho, alto, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + bloque_png(b"IHDR", cabecera)
            + bloque_png(b"IDAT", zlib.compress(filas, 9)) + bloque_png(b"IEND", b""))


def crear_pdf():
    texto = b"BT /F1 18 Tf 72 720 Td (Evidencia de demostracion SolTec) Tj ET"
    objetos = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
        b"/Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(texto)).encode() + b" >>\nstream\n" + texto + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    salida = b"%PDF-1.4\n"
    posiciones = []
    for i in range(len(objetos)):
        posiciones.append(len(salida))
        salida += str(i + 1).encode() + b" 0 obj\n" + objetos[i] + b"\nendobj\n"
    inicio = len(salida)
    salida += b"xref\n0 6\n0000000000 65535 f \n"
    for i in range(len(posiciones)):
        salida += ("%010d 00000 n \n" % posiciones[i]).encode()
    salida += b"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + str(inicio).encode() + b"\n%%EOF\n"
    return salida


PNG = crear_png()
PDF = crear_pdf()

if len(sys.argv) > 1 and sys.argv[1] == "--tamanos":
    print("png", len(PNG), hashlib.sha256(PNG).hexdigest())
    print("pdf", len(PDF), hashlib.sha256(PDF).hexdigest())
    sys.exit(0)

# utf-8-sig: PowerShell antepone un BOM a la primera linea
sys.stdin.reconfigure(encoding="utf-8-sig")
creados = 0
for linea in sys.stdin:
    ruta = linea.strip()
    if ruta == "":
        continue
    destino = os.path.join(BASE, ruta.replace("/", os.sep))
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    with open(destino, "wb") as archivo:
        archivo.write(PDF if ruta.endswith(".pdf") else PNG)
    creados += 1
print("Archivos creados:", creados)
