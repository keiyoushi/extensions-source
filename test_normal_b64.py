import base64

v1 = "==gJsJooAruHs0XkiOo6Z81bfyLy5HVCgc8+QWrPLUy2+b8SqVAKYP6xNJWU74t+vikMfYnhyf+T0AFMHG8q"
v2 = "=cUcz6M+DNySGyqzm7QLIVtoez/ExQXmj+s6YklIET+nzWVClcp+YWLPI0C27TZsvpUJOu7lbQSBsV59"

# Normal b64decode
for label, val in [("v1", v1), ("v2", v2)]:
    padded = val + '=' * ((4 - len(val) % 4) % 4)
    try:
        dec = base64.b64decode(padded).decode('latin1', errors='ignore')
        print(f"{label} normal b64 decode:", repr(dec))
    except Exception as e:
        print(f"{label} normal decode error:", e)
