import base64

v1 = "==gJsJooAruHs0XkiOo6Z81bfyLy5HVCgc8+QWrPLUy2+b8SqVAKYP6xNJWU74t+vikMfYnhyf+T0AFMHG8q"
v2 = "=cUcz6M+DNySGyqzm7QLIVtoez/ExQXmj+s6YklIET+nzWVClcp+YWLPI0C27TZsvpUJOu7lbQSBsV59"

for label, val in [("v1", v1), ("v2", v2)]:
    rev = val[::-1]
    padded = rev + '=' * ((4 - len(rev) % 4) % 4)
    try:
        dec = base64.b64decode(padded).decode('latin1', errors='ignore')
        print(f"{label} reversed b64 decode:", repr(dec))
    except Exception as e:
        print(f"{label} decode error:", e)
