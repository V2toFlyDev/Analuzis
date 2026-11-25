import shutil
import sys
import zipfile

if len(sys.argv) != 4:
    print("uso: adddex.py base.apk classes.dex salida.apk")
    sys.exit(2)

base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copyfile(base, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("clases añadidas a", out)
